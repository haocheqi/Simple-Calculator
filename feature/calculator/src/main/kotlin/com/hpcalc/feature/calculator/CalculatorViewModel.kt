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
import com.hpcalc.core.data.SettingsRepository
import com.hpcalc.core.math.CalculatorEngine
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.EvaluationContext
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

    val state: StateFlow<CalculatorUiState> =
        combine(local, history.observe(), settingsRepository.settings) { ui, entries, settings ->
            ui.copy(
                history = entries,
                settings = settings,
                angleUnit = settings.angle,
                displayMode = settings.displayMode
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = local.value
        )

    fun onEvent(event: CalculatorUiEvent) {
        when (event) {
            is CalculatorUiEvent.Append -> mutate {
                it.copy(expression = (it.expression + event.text).take(MAX_EXPRESSION))
            }

            CalculatorUiEvent.Delete -> mutate { it.copy(expression = it.expression.dropLast(1)) }

            CalculatorUiEvent.Clear -> mutate { it.copy(expression = "") }

            CalculatorUiEvent.Evaluate -> evaluate()

            is CalculatorUiEvent.SwitchMode -> mutate { it.copy(mode = event.mode) }

            is CalculatorUiEvent.SwitchAngle -> launchIo { settingsRepository.setAngle(event.unit) }

            is CalculatorUiEvent.SwitchDisplayMode -> launchIo {
                settingsRepository.setDisplayMode(event.mode)
            }

            is CalculatorUiEvent.SwitchTheme -> launchIo { settingsRepository.setTheme(event.theme) }

            is CalculatorUiEvent.Reuse -> mutate { it.copy(expression = event.expression.take(MAX_EXPRESSION)) }

            CalculatorUiEvent.ClearHistory -> launchIo { history.clear() }

            is CalculatorUiEvent.DeleteHistory -> launchIo { history.deleteById(event.id) }

            is CalculatorUiEvent.SetMatrixSize -> mutate { it.copy(matrix = it.matrix.resized(event.size)) }

            is CalculatorUiEvent.SetMatrixCell -> mutate {
                it.copy(matrix = it.matrix.withCell(event.side, event.row, event.column, event.value))
            }

            is CalculatorUiEvent.SetMatrixOperation -> mutate {
                it.copy(matrix = it.matrix.copy(operation = event.operation))
            }

            CalculatorUiEvent.EvaluateMatrix -> evaluateMatrix()
        }
    }

    private fun evaluate() {
        val snapshot = local.value
        if (snapshot.expression.isBlank()) return
        viewModelScope.launch {
            val angleUnit = settingsRepository.settings.firstOrNull()?.angle ?: AngleUnit.RADIANS
            val outcome = withContext(Dispatchers.Default) {
                engine.evaluate(
                    mode = snapshot.mode,
                    input = snapshot.expression,
                    context = EvaluationContext(angleUnit = angleUnit, ans = snapshot.ans),
                    displayMode = snapshot.displayMode
                )
            }
            mutate { it.copy(result = outcome.block, ans = outcome.value ?: it.ans) }
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

    private fun evaluateMatrix() {
        val matrix = local.value.matrix
        val displayMode = local.value.displayMode
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
            val outcome = withContext(Dispatchers.Default) { engine.matrix(operation, displayMode) }
            mutate { it.copy(result = outcome.block) }
        }
    }

    private fun mutate(transform: (CalculatorUiState) -> CalculatorUiState) {
        val next = transform(local.value)
        local.value = next
        savedStateHandle[KEY_EXPRESSION] = next.expression
        savedStateHandle[KEY_MODE] = next.mode.name
        savedStateHandle[KEY_ANS] = next.ans?.toPlainString()
        savedStateHandle[KEY_MATRIX_SIZE] = next.matrix.size
        savedStateHandle[KEY_MATRIX_OPERATION] = next.matrix.operation.name
        savedStateHandle[KEY_MATRIX_LEFT] = next.matrix.left.flatten().joinToString(CELL_SEPARATOR)
        savedStateHandle[KEY_MATRIX_RIGHT] = next.matrix.right.flatten().joinToString(CELL_SEPARATOR)
    }

    private fun restore(): CalculatorUiState {
        val size = savedStateHandle.get<Int>(KEY_MATRIX_SIZE)
            ?.coerceIn(MIN_MATRIX_SIZE, MAX_MATRIX_SIZE)
            ?: 2
        val left = savedStateHandle.get<String>(KEY_MATRIX_LEFT)?.toGrid(size) ?: blankMatrixGrid(size)
        val right = savedStateHandle.get<String>(KEY_MATRIX_RIGHT)?.toGrid(size) ?: blankMatrixGrid(size)

        return CalculatorUiState(
            mode = savedStateHandle.get<String>(KEY_MODE)
                ?.let { runCatching { CalculatorMode.valueOf(it) }.getOrNull() }
                ?: CalculatorMode.BASIC,
            expression = savedStateHandle.get<String>(KEY_EXPRESSION) ?: "",
            ans = savedStateHandle.get<String>(KEY_ANS)?.toBigDecimalOrNull(),
            matrix = MatrixUiState(
                size = size,
                left = left,
                right = right,
                operation = savedStateHandle.get<String>(KEY_MATRIX_OPERATION)
                    ?.let { runCatching { MatrixOperationKind.valueOf(it) }.getOrNull() }
                    ?: MatrixOperationKind.ADD
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

        private const val CELL_SEPARATOR = "|"
        private const val KEY_EXPRESSION = "expression"
        private const val KEY_MODE = "mode"
        private const val KEY_ANS = "ans"
        private const val KEY_MATRIX_SIZE = "matrixSize"
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

private fun MatrixUiState.resized(size: Int): MatrixUiState {
    fun resize(grid: List<List<String>>): List<List<String>> =
        List(size) { row -> List(size) { column -> grid.getOrNull(row)?.getOrNull(column) ?: "" } }

    return copy(size = size, left = resize(left), right = resize(right))
}

private fun MatrixUiState.withCell(
    side: MatrixSide,
    row: Int,
    column: Int,
    value: String
): MatrixUiState {
    fun set(grid: List<List<String>>): List<List<String>> = grid.mapIndexed { r, cells ->
        if (r != row) cells else cells.mapIndexed { c, cell -> if (c == column) value else cell }
    }

    return when (side) {
        MatrixSide.LEFT -> copy(left = set(left))
        MatrixSide.RIGHT -> copy(right = set(right))
    }
}

/** 留空的单元格按 0 处理；无法解析的数字整张表作废（返回 null，界面保持原结果）。 */
private fun List<List<String>>.toMatrix(): Matrix? {
    val rows = map { row ->
        row.map { cell ->
            cell.trim().ifEmpty { "0" }.toBigDecimalOrNull() ?: return null
        }
    }
    return if (rows.isEmpty()) null else Matrix(rows)
}

private fun String.toGrid(size: Int): List<List<String>> {
    val cells = split(CELL_TOKEN)
    return List(size) { row -> List(size) { column -> cells.getOrNull(row * size + column) ?: "" } }
}

private const val CELL_TOKEN = "|"