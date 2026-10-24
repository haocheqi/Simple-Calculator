package com.hpcalc.core.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 把内部 50 位精度的结果格式化为面向用户的字符串。
 *
 * 规则与 v2.0 一致：按有效数字四舍五入、去掉尾部零，
 * 极大/极小的量级改用工程记数法（`1.234E-8`），其余用普通小数形式。
 * 显示默认 16 位有效数字，比内部精度低，但远超 `Double`。
 */
fun formatResult(value: BigDecimal, significantDigits: Int = 16): String {
    val rounded = value.round(MathContext(significantDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
    val abs = rounded.abs()
    val needsScientific = abs != BigDecimal.ZERO &&
        (abs >= BigDecimal("1e16") || abs < BigDecimal("1e-6"))
    return if (needsScientific) rounded.toEngineeringString() else rounded.toPlainString()
}