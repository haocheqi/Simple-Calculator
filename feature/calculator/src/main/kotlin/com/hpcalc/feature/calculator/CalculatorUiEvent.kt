package com.hpcalc.feature.calculator

import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.ThemeMode

/** 界面发出的全部意图。ViewModel 是唯一消费方。 */
sealed interface CalculatorUiEvent {

    /** 追加文本到当前表达式（键盘、函数条、常量都用它）。 */
    data class Append(val text: String) : CalculatorUiEvent

    data object Delete : CalculatorUiEvent

    data object Clear : CalculatorUiEvent

    data object Evaluate : CalculatorUiEvent

    data class SwitchMode(val mode: CalculatorMode) : CalculatorUiEvent

    data class SwitchAngle(val unit: AngleUnit) : CalculatorUiEvent

    data class SwitchDisplayMode(val mode: DisplayMode) : CalculatorUiEvent

    data class SwitchTheme(val theme: ThemeMode) : CalculatorUiEvent

    /** 从历史里取回一条表达式。 */
    data class Reuse(val expression: String) : CalculatorUiEvent

    data object ClearHistory : CalculatorUiEvent

    data class DeleteHistory(val id: Long) : CalculatorUiEvent

    /** 置顶 / 取消置顶一条历史。 */
    data class SetHistoryPinned(val id: Long, val pinned: Boolean) : CalculatorUiEvent

    data class SetMatrixRows(val rows: Int) : CalculatorUiEvent

    data class SetMatrixColumns(val columns: Int) : CalculatorUiEvent

    /** 只对乘法有意义：右矩阵的列数。 */
    data class SetMatrixRightColumns(val columns: Int) : CalculatorUiEvent

    data class SetMatrixCell(
        val side: MatrixSide,
        val row: Int,
        val column: Int,
        val value: String
    ) : CalculatorUiEvent

    data class SetMatrixOperation(val operation: MatrixOperationKind) : CalculatorUiEvent

    data object EvaluateMatrix : CalculatorUiEvent

    /** 符号模式：修改代入用的变量名。 */
    data class SetSubstituteVariable(val name: String) : CalculatorUiEvent

    /** 符号模式：修改代入值，允许是表达式。 */
    data class SetSubstituteValue(val value: String) : CalculatorUiEvent

    /** 符号模式：把当前表达式中的变量替换成给定值并求结果。 */
    data object Substitute : CalculatorUiEvent
}

enum class MatrixSide { LEFT, RIGHT }