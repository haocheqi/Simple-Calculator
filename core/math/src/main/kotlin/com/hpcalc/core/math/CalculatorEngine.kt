package com.hpcalc.core.math

import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.EvaluationResult
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MatrixOperation
import com.hpcalc.core.model.MatrixResult
import com.hpcalc.core.model.SymbolicResult
import com.hpcalc.core.model.UnsupportedFeature
import com.hpcalc.core.model.toMathBlock
import java.math.BigDecimal

/**
 * 把「模式 + 输入」翻译成「可渲染结果 + 新 ans」的唯一入口。
 *
 * 这里刻意不含协程、不含 Android 依赖，因此可以直接用 JVM 单测覆盖；
 * ViewModel 只负责状态搬运与持久化，不再参与求值决策。
 */
class CalculatorEngine(
    private val numeric: HighPrecisionEvaluator = HighPrecisionEvaluator(),
    private val symbolic: PolynomialCalculator = PolynomialCalculator(),
    private val matrix: MatrixCalculator = MatrixCalculator(),
    private val rational: RationalEvaluator = RationalEvaluator()
) {

    /** 一次求值的结果。[value] 非空表示这次结果应当成为下一次的 `ans`。 */
    data class Outcome(
        val block: MathBlock,
        val succeeded: Boolean,
        val value: BigDecimal? = null
    )

    fun evaluate(
        mode: CalculatorMode,
        input: String,
        context: EvaluationContext,
        displayMode: DisplayMode
    ): Outcome {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Outcome(MathBlock.Text(""), succeeded = false)
        }
        return when (mode) {
            CalculatorMode.BASIC, CalculatorMode.SCIENTIFIC -> {
                if (displayMode == DisplayMode.EXACT_FRACTION) {
                    when (val exact = rational.evaluate(trimmed)) {
                        is RationalResult.Exact -> return Outcome(
                            block = exact.toMathBlock(),
                            succeeded = true,
                            value = exact.toBigDecimal()
                        )
                        // 只有「这一步无法精确」才回退小数；
                        // 真正的错误（除以零、括号不配对）必须如实报出来。
                        is RationalResult.Error -> if (exact.error !is MathError.Unsupported) {
                            return Outcome(MathBlock.Error(exact.error), succeeded = false)
                        }
                    }
                }
                when (val result = numeric.evaluate(trimmed, context)) {
                    is EvaluationResult.Success -> Outcome(
                        block = result.value.toMathBlock(displayMode),
                        succeeded = true,
                        value = result.value
                    )
                    is EvaluationResult.Error -> Outcome(MathBlock.Error(result.error), succeeded = false)
                }
            }

            CalculatorMode.SYMBOLIC -> {
                symbolic.setAns(context.ans)
                when (val result = symbolic.simplify(trimmed)) {
                    is SymbolicResult.Success -> Outcome(
                        block = result.toMathBlock(),
                        succeeded = true,
                        // 化简结果能解析成数字时才更新 ans（例如 x-x 得到 0）。
                        value = result.expression.toBigDecimalOrNull()
                    )
                    is SymbolicResult.Error -> Outcome(MathBlock.Error(result.error), succeeded = false)
                }
            }

            // 矩阵模式由专用面板驱动，不走表达式求值。
            CalculatorMode.MATRIX -> Outcome(
                MathBlock.Error(MathError.Unsupported(UnsupportedFeature.MATRIX_PANEL)),
                succeeded = false
            )
        }
    }

    fun matrix(operation: MatrixOperation, displayMode: DisplayMode): Outcome =
        when (val result = matrix.evaluate(operation)) {
            is MatrixResult.Success -> Outcome(result.matrix.toMathBlock(), succeeded = true)
            is MatrixResult.Scalar -> Outcome(result.value.toMathBlock(displayMode), succeeded = true, value = result.value)
            is MatrixResult.Text -> Outcome(MathBlock.Text(result.value), succeeded = true)
            is MatrixResult.Error -> Outcome(MathBlock.Error(result.error), succeeded = false)
        }

    /**
     * 符号模式的代入求值。
     *
     * 代入值本身也交给数值求值器，所以 `π/2`、`1+1`、`2^10` 都能直接填，
     * 不必让用户先自己算出一个小数——这是相对 v2.0 的改进（v2.0 只收字面量）。
     *
     * 表达式里若有别的变量（`x+y` 只给 `x`），`PolynomialCalculator` 会报
     * `DomainError("missing value for 'y'")`，界面上直接显示出来。
     */
    fun substitute(
        expression: String,
        variable: String,
        value: String,
        context: EvaluationContext,
        displayMode: DisplayMode
    ): Outcome {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return Outcome(MathBlock.Text(""), succeeded = false)

        val name = variable.trim()
        if (!VARIABLE_NAME.matches(name)) {
            val detail = if (name.isEmpty()) "variable name is empty" else "invalid variable name '$name'"
            return Outcome(MathBlock.Error(MathError.ParseError(detail)), succeeded = false)
        }

        val valueExpression = value.trim()
        if (valueExpression.isEmpty()) {
            return Outcome(
                MathBlock.Error(MathError.ParseError("missing value for '$name'")),
                succeeded = false
            )
        }

        val assigned = when (val evaluated = numeric.evaluate(valueExpression, context)) {
            is EvaluationResult.Success -> evaluated.value
            is EvaluationResult.Error -> return Outcome(MathBlock.Error(evaluated.error), succeeded = false)
        }

        return when (val result = symbolic.substitute(trimmed, mapOf(name to assigned))) {
            is SymbolicResult.Success -> {
                val numericResult = result.expression.toBigDecimalOrNull()
                Outcome(
                    block = numericResult?.toMathBlock(displayMode) ?: MathBlock.Text(result.expression),
                    succeeded = true,
                    value = numericResult
                )
            }
            is SymbolicResult.Error -> Outcome(MathBlock.Error(result.error), succeeded = false)
        }
    }
}

/** 多项式求值器的变量名规则：字母开头，后接字母或数字。 */
private val VARIABLE_NAME = Regex("[\\p{L}][\\p{L}\\p{N}]*")