package com.hpcalc.core.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** 求值上下文：精度、角度单位、上一次结果。 */
data class EvaluationContext(
    val mathContext: MathContext = DEFAULT_MATH_CONTEXT,
    val angleUnit: AngleUnit = AngleUnit.RADIANS,
    val ans: BigDecimal? = null
) {
    companion object {
        /** 内部计算精度：50 位有效数字。显示层再降到 16 位。 */
        val DEFAULT_MATH_CONTEXT: MathContext = MathContext(50, RoundingMode.HALF_UP)
    }
}

/** 数值求值结果。错误分支携带结构化 [MathError]，不再是裸字符串。 */
sealed interface EvaluationResult {
    data class Success(val value: BigDecimal) : EvaluationResult
    data class Error(val error: MathError) : EvaluationResult
}

/** 符号（多项式）求值结果。 */
sealed interface SymbolicResult {
    data class Success(val expression: String) : SymbolicResult
    data class Error(val error: MathError) : SymbolicResult
}

/** 矩阵求值结果。 */
sealed interface MatrixResult {
    data class Success(val matrix: Matrix) : MatrixResult
    data class Scalar(val value: BigDecimal) : MatrixResult
    data class Text(val value: String) : MatrixResult
    data class Error(val error: MathError) : MatrixResult
}

interface NumericEvaluator {
    fun evaluate(expression: String, context: EvaluationContext = EvaluationContext()): EvaluationResult
}

interface SymbolicEvaluator {
    fun simplify(expression: String): SymbolicResult
    fun substitute(expression: String, values: Map<String, BigDecimal>): SymbolicResult
}

interface MatrixEvaluator {
    fun evaluate(operation: MatrixOperation): MatrixResult
}