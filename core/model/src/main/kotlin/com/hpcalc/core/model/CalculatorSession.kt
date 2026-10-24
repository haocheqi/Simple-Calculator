package com.hpcalc.core.model

import java.math.BigDecimal

/**
 * 一次计算会话的跨次状态：上一次结果与当前角度单位。
 *
 * v2.0 把 `ans` 放在 `CalculatorViewModel` 的私有 `var` 里，旋屏即丢失且无法序列化。
 * 提到模型层后，阶段 2 可以让 `SavedStateHandle` 直接持久化这个对象。
 */
data class CalculatorSession(
    val ans: BigDecimal? = null,
    val angleUnit: AngleUnit = AngleUnit.RADIANS
) {
    fun recordAns(value: BigDecimal): CalculatorSession = copy(ans = value)

    fun withAngleUnit(unit: AngleUnit): CalculatorSession = copy(angleUnit = unit)

    fun context(mathContext: java.math.MathContext = EvaluationContext.DEFAULT_MATH_CONTEXT): EvaluationContext =
        EvaluationContext(mathContext = mathContext, angleUnit = angleUnit, ans = ans)
}