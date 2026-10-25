package com.hpcalc.core.math

import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.EvaluationResult
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MatrixResult
import com.hpcalc.core.model.SymbolicResult
import com.hpcalc.core.model.toMathBlock
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** 工程记数法适用的量级下限/上限。[1000, +∞) 或 (0, 0.001) 之间才值得换记数法。 */
private val ENGINEERING_UPPER = BigDecimal("1000")
private val ENGINEERING_LOWER = BigDecimal("0.001")

/**
 * 按显示模式把高精度数值转成字符串。
 *
 * 注意：不能直接用 [BigDecimal.toEngineeringString]。它只在 adjusted exponent < -6
 * 或 scale < 0 时才输出 `E` 记号，像 `12345678.9` 这类数仍然原样返回，
 * 导致「工程记数」模式形同虚设。这里自己算 3 的倍数指数。
 */
fun BigDecimal.toDisplayString(mode: DisplayMode, significantDigits: Int = 16): String {
    val rounded = round(MathContext(significantDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
    return when (mode) {
        DisplayMode.DECIMAL -> formatResult(this, significantDigits)

        DisplayMode.ENGINEERING -> when {
            rounded.compareTo(BigDecimal.ZERO) == 0 -> "0"
            // 千以内的小数换记数法只会更难读，保持原样。
            rounded.abs() >= ENGINEERING_UPPER || rounded.abs() < ENGINEERING_LOWER ->
                rounded.toEngineeringNotation()
            else -> rounded.toPlainString()
        }
    }
}

private fun BigDecimal.toEngineeringNotation(): String {
    val sign = if (signum() < 0) "-" else ""
    val magnitude = abs()
    val exponent = magnitude.precision() - magnitude.scale() - 1
    val engineeringExponent = Math.floorDiv(exponent, 3) * 3
    val mantissa = magnitude.movePointLeft(engineeringExponent).stripTrailingZeros()
    return "$sign${mantissa.toPlainString()}E$engineeringExponent"
}

fun BigDecimal.toMathBlock(mode: DisplayMode, significantDigits: Int = 16): MathBlock =
    MathBlock.Number(toDisplayString(mode, significantDigits))

/**
 * 各求值结果到呈现树的第一等转换。
 *
 * 界面只接受 [MathBlock]，不再自己拼接字符串——这是「所有输出都经 MathBlock 渲染」的落点。
 */
fun EvaluationResult.toMathBlock(mode: DisplayMode): MathBlock = when (this) {
    is EvaluationResult.Success -> value.toMathBlock(mode)
    is EvaluationResult.Error -> MathBlock.Error(error)
}

fun SymbolicResult.toMathBlock(): MathBlock = when (this) {
    // 化简结果是多项式字符串，没有结构化 AST 可复用，按原样文本呈现。
    is SymbolicResult.Success -> MathBlock.Text(expression)
    is SymbolicResult.Error -> MathBlock.Error(error)
}

fun MatrixResult.toMathBlock(mode: DisplayMode): MathBlock = when (this) {
    is MatrixResult.Success -> matrix.toMathBlock()
    is MatrixResult.Scalar -> value.toMathBlock(mode)
    is MatrixResult.Text -> MathBlock.Text(value)
    is MatrixResult.Error -> MathBlock.Error(error)
}