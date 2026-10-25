package com.hpcalc.feature.calculator

import com.hpcalc.core.data.HistoryEntity
import com.hpcalc.core.data.Settings
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.MathBlock
import java.math.BigDecimal

/** 矩阵面板支持的运算。 */
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

    fun withCell(side: MatrixSide, row: Int, column: Int, value: String): MatrixUiState {
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
    val displayMode: DisplayMode = DisplayMode.DECIMAL,
    val angleUnit: AngleUnit = AngleUnit.RADIANS,
    val ans: BigDecimal? = null,
    val history: List<HistoryEntity> = emptyList(),
    val settings: Settings = Settings(),
    val matrix: MatrixUiState = MatrixUiState(),
    /** 符号模式代入求值用的变量名，默认 `x`。 */
    val substituteVariable: String = "x",
    /** 代入值，允许填写表达式（`π/2`）。 */
    val substituteValue: String = ""
) {
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