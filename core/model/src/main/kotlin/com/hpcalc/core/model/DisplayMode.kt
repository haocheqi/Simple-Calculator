package com.hpcalc.core.model

/**
 * 数值结果的呈现方式。
 *
 * 阶段 2 提供 [DECIMAL] 与 [ENGINEERING]；
 * [EXACT_FRACTION] 依赖阶段 3 的精确分数求值器（`RationalEvaluator`），届时再加入。
 */
enum class DisplayMode {
    /** 常规小数；极大或极小量级自动切到工程记数法。 */
    DECIMAL,

    /** 始终用工程记数法（指数为 3 的倍数）。 */
    ENGINEERING
}