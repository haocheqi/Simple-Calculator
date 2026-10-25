package com.hpcalc.core.model

/**
 * 数值结果的呈现方式。
 *
 * [EXACT_FRACTION] 只在能保证精确时生效（见 `RationalEvaluator`）；
 * 遇到 sin/ln/√ 这类必然产生无理数的运算会自动退回 [DECIMAL]，而不是给出假分数。
 */
enum class DisplayMode {
    /** 常规小数；极大或极小量级自动切到工程记数法。 */
    DECIMAL,

    /** 始终用工程记数法（指数为 3 的倍数）。 */
    ENGINEERING,

    /** 精确分数：`1/3` 渲染成真分式，分母为 1 时退化成整数。 */
    EXACT_FRACTION
}