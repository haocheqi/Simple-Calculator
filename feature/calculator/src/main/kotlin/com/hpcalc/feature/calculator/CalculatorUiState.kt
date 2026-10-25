package com.hpcalc.feature.calculator

import com.hpcalc.core.data.HistoryEntity
import com.hpcalc.core.data.Settings
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.plainText
import java.math.BigDecimal

/** 矩阵面板支持的运算。 */
/**
 * 单元格输入过滤：只保留十进制数字符。
 *
 * 不做这一步时，界面允许填 `abc`，按计算后 `toBigDecimalOrNull()` 失败、`toMatrix()` 返回 null，
 * 于是**什么都不会发生**——用户只会觉得按钮坏了。
 */
internal fun sanitizeCellInput(raw: String): String =
    raw.filter { it.isDigit() || it == '.' || it == '-' || it == '+' || it == 'e' || it == 'E' }
        .take(MatrixUiState.MAX_CELL_LENGTH)
enum class MatrixOperationKind {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DETERMINANT,
    INVERSE,
    TRANSPOSE,
    RANK;

    /** 只有二元运算才需要填第二个矩阵。 */
    val needsRightOperand: Boolean
        get() = this == ADD || this == SUBTRACT || this == MULTIPLY
}

/**
 * 右矩阵的行数。
 *
 * 乘法要求 `A(m×n) · B(n×p)`，所以 B 的行数由 A 的列数决定，用户不能改；
 * 加减要求同形，B 直接沿用 A 的形状。
 */
internal fun rightRowsFor(operation: MatrixOperationKind, leftRows: Int, leftColumns: Int): Int =
    if (operation == MatrixOperationKind.MULTIPLY) leftColumns else leftRows

/** 右矩阵的列数。只有乘法可以自由选，其余情况跟随左矩阵。 */
internal fun rightColumnsFor(
    operation: MatrixOperationKind,
    leftColumns: Int,
    rightColumns: Int
): Int = if (operation == MatrixOperationKind.MULTIPLY) rightColumns else leftColumns

/**
 * 矩阵面板的输入与选择。单元格允许留空，按 0 处理。
 *
 * 左矩阵是 `rows × columns`，右矩阵的形状由运算种类推导（见 [rightRows]/[rightColumnCount]），
 * 因此所有改动都走 [withRows]/[withColumns]/[withRightColumns]/[withOperation]，
 * 由它们保证两个网格与声明的尺寸一致。
 */
data class MatrixUiState(
    val rows: Int = 2,
    val columns: Int = 2,
    val rightColumns: Int = 2,
    val left: List<List<String>> = blankGrid(2, 2),
    val right: List<List<String>> = blankGrid(2, 2),
    val operation: MatrixOperationKind = MatrixOperationKind.ADD
) {
    val rightRows: Int get() = rightRowsFor(operation, rows, columns)

    val rightColumnCount: Int get() = rightColumnsFor(operation, columns, rightColumns)

    companion object {
        /** 单个单元格的字符上限。够写 `1.234567890123e-10`，又不至于把输入框塞爆。 */
        const val MAX_CELL_LENGTH = 24
    }

    fun withRows(value: Int): MatrixUiState =
        copy(rows = value.coerceIn(CalculatorViewModel.MIN_MATRIX_SIZE, CalculatorViewModel.MAX_MATRIX_SIZE))
            .reshaped()

    fun withColumns(value: Int): MatrixUiState =
        copy(columns = value.coerceIn(CalculatorViewModel.MIN_MATRIX_SIZE, CalculatorViewModel.MAX_MATRIX_SIZE))
            .reshaped()

    fun withRightColumns(value: Int): MatrixUiState =
        copy(rightColumns = value.coerceIn(CalculatorViewModel.MIN_MATRIX_SIZE, CalculatorViewModel.MAX_MATRIX_SIZE))
            .reshaped()

    fun withOperation(kind: MatrixOperationKind): MatrixUiState = copy(operation = kind).reshaped()

    fun withCell(side: MatrixSide, row: Int, column: Int, raw: String): MatrixUiState {
        // 单元格只收十进制数字符：字母和空格直接丢掉，免得用户填出「算不了」的矩阵。
        val value = sanitizeCellInput(raw)
        fun set(grid: List<List<String>>): List<List<String>> = grid.mapIndexed { r, cells ->
            if (r != row) cells else cells.mapIndexed { c, cell -> if (c == column) value else cell }
        }

        return when (side) {
            MatrixSide.LEFT -> copy(left = set(left))
            MatrixSide.RIGHT -> copy(right = set(right))
        }
    }

    /** 把两个网格裁/补到声明的尺寸，保留索引重叠部分的已填内容。 */
    private fun reshaped(): MatrixUiState = copy(
        left = left.paddedTo(rows, columns),
        right = right.paddedTo(rightRows, rightColumnCount)
    )
}

private fun blankGrid(rows: Int, columns: Int): List<List<String>> = List(rows) { List(columns) { "" } }

fun blankMatrixGrid(rows: Int, columns: Int): List<List<String>> = blankGrid(rows, columns)

private fun List<List<String>>.paddedTo(rows: Int, columns: Int): List<List<String>> =
    List(rows) { row -> List(columns) { column -> getOrNull(row)?.getOrNull(column) ?: "" } }

/** 计算器界面的全部可见状态，单一不可变对象。 */
data class CalculatorUiState(
    val mode: CalculatorMode = CalculatorMode.BASIC,
    val expression: String = "",
    val result: MathBlock = MathBlock.Number("0"),


    val ans: BigDecimal? = null,
    val history: List<HistoryEntity> = emptyList(),
    val settings: Settings = Settings(),
    val matrix: MatrixUiState = MatrixUiState(),
    /** 表达式里的插入点，取值 `0..expression.length`。 */
    val caret: Int = 0,
    /** 同一结果能写成精确分数时的形态；为空表示这个结果只有小数（例如 `sin(1)`）。 */
    val exactResult: MathBlock? = null,
    /** 当前是否显示精确分数形态。S⇔D 切换它。 */
    val showsExactFraction: Boolean = false,
    /** 符号模式代入求值用的变量名，默认 `x`。 */
    val substituteVariable: String = "x",
    /** 代入值，允许填写表达式（`π/2`）。 */
    val substituteValue: String = ""
) {
    /** 结果区真正要画的东西：有精确形态且当前选中它时画分数，否则画小数。 */
    val shownResult: MathBlock
        get() = if (showsExactFraction && exactResult != null) exactResult else result

    /**
     * S⇔D 只在两种形态**看起来不一样**时才可用。
     *
     * 整数结果（`5`、`1/2` 算出的 `0.5` 除外）的小数形态与分数形态是同一个 `5`，
     * 给出按钮点了却没变化，比不给更让人困惑。
     */
    val canToggleForm: Boolean
        get() = exactResult != null && exactResult.plainText() != result.plainText()

    /** 光标是否停在末尾——界面据此决定是否画插入符。 */
    val caretAtEnd: Boolean get() = caret >= expression.length

    val showsKeypad: Boolean get() = mode != CalculatorMode.MATRIX

    val showsFunctionStrip: Boolean
        get() = mode == CalculatorMode.SCIENTIFIC || mode == CalculatorMode.SYMBOLIC

    /** 代入条只属于符号模式；其他模式没有变量可言。 */
    val showsSubstitutionStrip: Boolean get() = mode == CalculatorMode.SYMBOLIC

    val canSubstitute: Boolean
        get() = expression.isNotBlank() &&
            substituteVariable.isNotBlank() &&
            substituteValue.isNotBlank()
}