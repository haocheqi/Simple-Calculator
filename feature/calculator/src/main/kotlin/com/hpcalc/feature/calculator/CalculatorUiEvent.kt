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

    data class SetMatrixSize(val size: Int) : CalculatorUiEvent

    data class SetMatrixCell(
        val side: MatrixSide,
        val row: Int,
        val column: Int,
        val value: String
    ) : CalculatorUiEvent

    data class SetMatrixOperation(val operation: MatrixOperationKind) : CalculatorUiEvent

    data object EvaluateMatrix : CalculatorUiEvent
}

enum class MatrixSide { LEFT, RIGHT }