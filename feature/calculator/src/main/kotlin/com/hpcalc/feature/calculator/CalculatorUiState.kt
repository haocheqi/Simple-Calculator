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

/** 矩阵面板的输入与选择。单元格允许留空，按 0 处理。 */
data class MatrixUiState(
    val size: Int = 2,
    val left: List<List<String>> = blankGrid(2),
    val right: List<List<String>> = blankGrid(2),
    val operation: MatrixOperationKind = MatrixOperationKind.ADD
)

private fun blankGrid(size: Int): List<List<String>> = List(size) { List(size) { "" } }

fun blankMatrixGrid(size: Int): List<List<String>> = blankGrid(size)

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