package com.hpcalc.feature.calculator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hpcalc.core.data.HistoryDao
import com.hpcalc.core.data.HistoryEntity
import com.hpcalc.core.data.Settings
import com.hpcalc.core.data.SettingsRepository
import com.hpcalc.core.math.CalculatorEngine
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.Matrix
import com.hpcalc.core.model.MatrixOperation
import com.hpcalc.core.model.plainText
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 计算器界面的 ViewModel。
 *
 * 职责边界：
 * - 求值决策全部交给 [CalculatorEngine]（可在 JVM 上单测）；
 * - 这里只做状态搬运、持久化、以及把 Room/DataStore 的数据合流进 [CalculatorUiState]。
 *
 * 会话态用 [SavedStateHandle] 保存，进程被回收或屏幕旋转后表达式与结果都不丢。
 * 角度制与主题属于长期偏好，存在 DataStore 里，是唯一事实来源。
 */
class CalculatorViewModel(
    private val history: HistoryDao,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val engine = CalculatorEngine()
    private val local = MutableStateFlow(restore())

    init {
        restoreResult()
    }

    val state: StateFlow<CalculatorUiState> =
        combine(local, history.observe(), settingsRepository.settings) { ui, entries, settings ->
            // 设置只在这里合流一次；状态对象不再自带一份副本，免得它悄悄变旧。
            ui.copy(history = entries, settings = settings)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = local.value
        )

    fun onEvent(event: CalculatorUiEvent) {
        when (event) {
            is CalculatorUiEvent.Append -> mutate {
                val edited = insertAt(it.expression, it.caret, event.text, MAX_EXPRESSION)
                it.copy(expression = edited.expression, caret = edited.caret)
            }

            // 只删光标左边那一个字符：光标可以被点到中间，退格必须跟着光标走。
            CalculatorUiEvent.Delete -> mutate {
                val edited = deleteBefore(it.expression, it.caret)
                it.copy(expression = edited.expression, caret = edited.caret)
            }

            is CalculatorUiEvent.SetCaret -> mutate {
                it.copy(caret = moveCaret(it.expression, event.index))
            }

            // AC 必须连结果和错误一起清掉，否则清完空算式还留着一个旧结果。
            CalculatorUiEvent.Clear -> mutate {
                it.copy(
                    expression = "",
                    caret = 0,
                    result = MathBlock.Number("0"),
                    exactResult = null,
                    showsExactFraction = false
                )
            }

            CalculatorUiEvent.ToggleResultForm -> mutate {
                if (it.exactResult == null) it else it.copy(showsExactFraction = !it.showsExactFraction)
            }

            CalculatorUiEvent.Evaluate -> evaluate()

            is CalculatorUiEvent.SwitchMode -> mutate { it.copy(mode = event.mode) }

            is CalculatorUiEvent.SwitchAngle -> launchIo { settingsRepository.setAngle(event.unit) }

            is CalculatorUiEvent.SwitchDisplayMode -> launchIo {
                settingsRepository.setDisplayMode(event.mode)
            }

            is CalculatorUiEvent.SwitchTheme -> launchIo { settingsRepository.setTheme(event.theme) }

            is CalculatorUiEvent.Reuse -> mutate {
                it.copy(
                    expression = event.expression.take(MAX_EXPRESSION),
                    caret = event.expression.take(MAX_EXPRESSION).length
                )
            }

            CalculatorUiEvent.ClearHistory -> launchIo { history.clear() }

            is CalculatorUiEvent.DeleteHistory -> launchIo { history.deleteById(event.id) }

            is CalculatorUiEvent.SetHistoryPinned -> launchIo {
                history.setPinned(event.id, event.pinned)
            }

            is CalculatorUiEvent.SetMatrixRows -> mutate { it.copy(matrix = it.matrix.withRows(event.rows)) }

            is CalculatorUiEvent.SetMatrixColumns -> mutate {
                it.copy(matrix = it.matrix.withColumns(event.columns))
            }

            is CalculatorUiEvent.SetMatrixRightColumns -> mutate {
                it.copy(matrix = it.matrix.withRightColumns(event.columns))
            }

            is CalculatorUiEvent.SetMatrixCell -> mutate {
                it.copy(matrix = it.matrix.withCell(event.side, event.row, event.column, event.value))
            }

            is CalculatorUiEvent.SetMatrixOperation -> mutate {
                it.copy(matrix = it.matrix.withOperation(event.operation))
            }

            CalculatorUiEvent.EvaluateMatrix -> evaluateMatrix()

            is CalculatorUiEvent.SetSubstituteVariable -> mutate {
                it.copy(substituteVariable = event.name.take(MAX_VARIABLE_NAME))
            }

            is CalculatorUiEvent.SetSubstituteValue -> mutate {
                it.copy(substituteValue = event.value.take(MAX_SUBSTITUTE_VALUE))
            }

            CalculatorUiEvent.Substitute -> substitute()
        }
    }

    private fun evaluate() {
        val snapshot = local.value
        if (snapshot.expression.isBlank()) return
        viewModelScope.launch {
            // 显示模式必须现读 DataStore：`local` 里那份只在切换时才写，用它会让
            // 「精确分数」这个设置对求值完全失效——界面显示选中，结果照旧是小数。
            val preferences = currentSettings()
            val outcome = withContext(Dispatchers.Default) {
                engine.evaluate(
                    mode = snapshot.mode,
                    input = snapshot.expression,
                    context = EvaluationContext(angleUnit = preferences.angle, ans = snapshot.ans),
                    displayMode = preferences.displayMode
                )
            }
            mutate {
                it.copy(
                    result = outcome.block,
                    ans = outcome.value ?: it.ans,
                    exactResult = outcome.exact,

                    // 新结果回到设置里的默认形态；之后可以用 S⇔D 临时切。
                    showsExactFraction = preferences.displayMode == DisplayMode.EXACT_FRACTION
                )
            }
            if (outcome.succeeded) {
                history.insert(
                    HistoryEntity(
                        mode = snapshot.mode.name,
                        expression = snapshot.expression,
                        result = outcome.block.plainText()
                    )
                )
            }
        }
    }

    /** 符号模式的代入求值：结果同样进历史、同样刷新 ans。 */
    private fun substitute() {
        val snapshot = local.value
        if (!snapshot.canSubstitute) return
        viewModelScope.launch {
            val preferences = currentSettings()
            val outcome = withContext(Dispatchers.Default) {
                engine.substitute(
                    expression = snapshot.expression,
                    variable = snapshot.substituteVariable,
                    value = snapshot.substituteValue,
                    context = EvaluationContext(angleUnit = preferences.angle, ans = snapshot.ans),
                    displayMode = preferences.displayMode
                )
            }
            mutate { it.copy(result = outcome.block, ans = outcome.value ?: it.ans) }
            if (outcome.succeeded) {
                history.insert(
                    HistoryEntity(
                        mode = snapshot.mode.name,
                        expression = "${snapshot.expression} [${snapshot.substituteVariable}" +
                            "=${snapshot.substituteValue}]",
                        result = outcome.block.plainText()
                    )
                )
            }
        }
    }

    private fun evaluateMatrix() {
        val matrix = local.value.matrix
        val left = matrix.left.toMatrix() ?: return
        val right = matrix.right.toMatrix() ?: return

        val operation: MatrixOperation = when (matrix.operation) {
            MatrixOperationKind.ADD -> MatrixOperation.Add(left, right)
            MatrixOperationKind.SUBTRACT -> MatrixOperation.Subtract(left, right)
            MatrixOperationKind.MULTIPLY -> MatrixOperation.Multiply(left, right)
            MatrixOperationKind.DETERMINANT -> MatrixOperation.Determinant(left)
            MatrixOperationKind.INVERSE -> MatrixOperation.Inverse(left)
            MatrixOperationKind.TRANSPOSE -> MatrixOperation.Transpose(left)
            MatrixOperationKind.RANK -> MatrixOperation.Rank(left)
        }

        viewModelScope.launch {
            val preferences = currentSettings()
            val outcome = withContext(Dispatchers.Default) {
                engine.matrix(operation, preferences.displayMode)
            }
            mutate { it.copy(result = outcome.block) }
        }
    }

    /**
     * 旋转屏幕或进程重建后把结果补回来。
     *
     * [MathBlock] 不是可序列化类型，[SavedStateHandle] 只能存表达式、`ans` 这些原始值，
     * 于是结果区会退回 `0`——算式还在、结果却没了，看起来像算错了。这里静默重算一次：
     * 不写历史、不动 `ans`，失败也照原样显示（错误提示同样要复原）。
     *
     * 矩阵模式跳过：它的结果由面板驱动，无法从表达式还原。
     */
    private fun restoreResult() {
        val snapshot = local.value
        if (snapshot.mode == CalculatorMode.MATRIX || snapshot.expression.isBlank()) return
        viewModelScope.launch {
            val preferences = currentSettings()
            val outcome = withContext(Dispatchers.Default) {
                engine.evaluate(
                    mode = snapshot.mode,
                    input = snapshot.expression,
                    context = EvaluationContext(angleUnit = preferences.angle, ans = snapshot.ans),
                    displayMode = preferences.displayMode
                )
            }
            mutate {
                // 重算期间用户可能已经开始输入新算式，那就不要用旧结果盖掉。
                if (it.expression != snapshot.expression) {
                    it
                } else {
                    it.copy(
                        result = outcome.block,
                        exactResult = outcome.exact,
    
                        showsExactFraction = preferences.displayMode == DisplayMode.EXACT_FRACTION
                    )
                }
            }
        }
    }

    /**
     * 读一次设置。角度制与显示模式都以 DataStore 为唯一事实来源，缓存进 `local` 只会变旧。
     */
    private suspend fun currentSettings(): Settings =
        settingsRepository.settings.firstOrNull() ?: Settings()

    private fun mutate(transform: (CalculatorUiState) -> CalculatorUiState) {
        val next = transform(local.value)
        local.value = next
        savedStateHandle[KEY_EXPRESSION] = next.expression
        savedStateHandle[KEY_CARET] = next.caret
        savedStateHandle[KEY_MODE] = next.mode.name
        savedStateHandle[KEY_ANS] = next.ans?.toPlainString()
        savedStateHandle[KEY_MATRIX_ROWS] = next.matrix.rows
        savedStateHandle[KEY_MATRIX_COLUMNS] = next.matrix.columns
        savedStateHandle[KEY_MATRIX_RIGHT_COLUMNS] = next.matrix.rightColumns
        savedStateHandle[KEY_MATRIX_OPERATION] = next.matrix.operation.name
        savedStateHandle[KEY_MATRIX_LEFT] = next.matrix.left.flatten().joinToString(CELL_SEPARATOR)
        savedStateHandle[KEY_MATRIX_RIGHT] = next.matrix.right.flatten().joinToString(CELL_SEPARATOR)
        savedStateHandle[KEY_SUBSTITUTE_VARIABLE] = next.substituteVariable
        savedStateHandle[KEY_SUBSTITUTE_VALUE] = next.substituteValue
    }

    private fun restore(): CalculatorUiState {
        val operation = savedStateHandle.get<String>(KEY_MATRIX_OPERATION)
            ?.let { runCatching { MatrixOperationKind.valueOf(it) }.getOrNull() }
            ?: MatrixOperationKind.ADD
        val rows = savedStateHandle.get<Int>(KEY_MATRIX_ROWS)
            ?.coerceIn(MIN_MATRIX_SIZE, MAX_MATRIX_SIZE)
            ?: 2
        val columns = savedStateHandle.get<Int>(KEY_MATRIX_COLUMNS)
            ?.coerceIn(MIN_MATRIX_SIZE, MAX_MATRIX_SIZE)
            ?: 2
        val rightColumns = savedStateHandle.get<Int>(KEY_MATRIX_RIGHT_COLUMNS)
            ?.coerceIn(MIN_MATRIX_SIZE, MAX_MATRIX_SIZE)
            ?: 2

        // 右矩阵形状由左矩阵与运算种类推导，重建时用同一套函数，避免两处逻辑漂移。
        val rightRows = rightRowsFor(operation, rows, columns)
        val rightColumnCount = rightColumnsFor(operation, columns, rightColumns)
        val left = savedStateHandle.get<String>(KEY_MATRIX_LEFT)?.toGrid(rows, columns)
            ?: blankMatrixGrid(rows, columns)
        val right = savedStateHandle.get<String>(KEY_MATRIX_RIGHT)?.toGrid(rightRows, rightColumnCount)
            ?: blankMatrixGrid(rightRows, rightColumnCount)

        val expression = savedStateHandle.get<String>(KEY_EXPRESSION) ?: ""

        return CalculatorUiState(
            mode = savedStateHandle.get<String>(KEY_MODE)
                ?.let { runCatching { CalculatorMode.valueOf(it) }.getOrNull() }
                ?: CalculatorMode.BASIC,
            expression = expression,
            caret = savedStateHandle.get<Int>(KEY_CARET)?.coerceIn(0, expression.length)
                ?: expression.length,
            ans = savedStateHandle.get<String>(KEY_ANS)?.toBigDecimalOrNull(),
            substituteVariable = savedStateHandle.get<String>(KEY_SUBSTITUTE_VARIABLE) ?: "x",
            substituteValue = savedStateHandle.get<String>(KEY_SUBSTITUTE_VALUE) ?: "",
            matrix = MatrixUiState(
                rows = rows,
                columns = columns,
                rightColumns = rightColumns,
                left = left,
                right = right,
                operation = operation
            )
        )
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    companion object {
        const val MAX_EXPRESSION = 512
        const val MIN_MATRIX_SIZE = 2
        const val MAX_MATRIX_SIZE = 6
        const val MAX_VARIABLE_NAME = 16
        const val MAX_SUBSTITUTE_VALUE = 128

        private const val CELL_SEPARATOR = "|"
        private const val KEY_EXPRESSION = "expression"
        private const val KEY_CARET = "caret"
        private const val KEY_MODE = "mode"
        private const val KEY_ANS = "ans"
        private const val KEY_SUBSTITUTE_VARIABLE = "substituteVariable"
        private const val KEY_SUBSTITUTE_VALUE = "substituteValue"
        private const val KEY_MATRIX_ROWS = "matrixRows"
        private const val KEY_MATRIX_COLUMNS = "matrixColumns"
        private const val KEY_MATRIX_RIGHT_COLUMNS = "matrixRightColumns"
        private const val KEY_MATRIX_OPERATION = "matrixOperation"
        private const val KEY_MATRIX_LEFT = "matrixLeft"
        private const val KEY_MATRIX_RIGHT = "matrixRight"

        fun factory(history: HistoryDao, settings: SettingsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CalculatorViewModel(history, settings, createSavedStateHandle())
                }
            }
    }
}

/** 留空的单元格按 0 处理；无法解析的数字整张表作废（返回 null，界面保持原结果）。 */
private fun List<List<String>>.toMatrix(): Matrix? {
    if (isEmpty() || any { it.isEmpty() }) return null
    val rows = map { row ->
        row.map { cell ->
            cell.trim().ifEmpty { "0" }.toBigDecimalOrNull() ?: return null
        }
    }
    return Matrix(rows)
}

private fun String.toGrid(rows: Int, columns: Int): List<List<String>> {
    val cells = split(CELL_TOKEN)
    return List(rows) { row -> List(columns) { column -> cells.getOrNull(row * columns + column) ?: "" } }
}

private const val CELL_TOKEN = "|"
