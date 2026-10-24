package com.hpcalc.core.math

import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.EvaluationResult
import com.hpcalc.core.model.MathError
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数值求值器回归测试。
 *
 * 重点是 v2.0 的崩溃路径（`1+`、`log(`、首次 `ans`）与三项数学缺陷
 * （一元负号优先级、反三角角度还原、阶乘上限），逐条对应 docs/ACCEPTANCE.md。
 */
class HighPrecisionEvaluatorTest {

    private val evaluator = HighPrecisionEvaluator()

    private fun success(
        expression: String,
        context: EvaluationContext = EvaluationContext()
    ): BigDecimal {
        val result = evaluator.evaluate(expression, context)
        assertTrue("expected success for `$expression`, got $result", result is EvaluationResult.Success)
        return (result as EvaluationResult.Success).value
    }

    private fun failure(
        expression: String,
        context: EvaluationContext = EvaluationContext()
    ): MathError {
        val result = evaluator.evaluate(expression, context)
        assertTrue("expected failure for `$expression`, got $result", result is EvaluationResult.Error)
        return (result as EvaluationResult.Error).error
    }

    // ------------------------------------------------------ 崩溃红线 A01-A08

    @Test
    fun `A01 trailing plus reports error instead of crashing`() {
        assertTrue(failure("1+") is MathError.ParseError)
    }

    @Test
    fun `A02 unclosed function reports error instead of crashing`() {
        assertTrue(failure("log(") is MathError.ParseError)
    }

    @Test
    fun `A03 ans before any result reports error instead of crashing`() {
        val error = failure("ans")
        assertTrue(error is MathError.NotAvailable)
    }

    @Test
    fun `A04 trailing multiply reports error instead of crashing`() {
        assertTrue(failure("2*") is MathError.ParseError)
    }

    @Test
    fun `A05 lone closing paren reports error instead of crashing`() {
        assertTrue(failure(")") is MathError.ParseError)
    }

    @Test
    fun `A06 blank expression reports error instead of crashing`() {
        assertTrue(failure("") is MathError.ParseError)
    }

    @Test
    fun `A07 deep nesting is rejected before the stack overflows`() {
        val nested = "(".repeat(70) + "1" + ")".repeat(70)
        assertEquals(MathError.DepthLimitExceeded, failure(nested))
    }

    @Test
    fun `A08 division by zero reports error instead of crashing`() {
        assertEquals(MathError.DivisionByZero, failure("1/0"))
    }

    // ------------------------------------------------------ 数学正确性 B01-B07

    @Test
    fun `B01 unary minus binds looser than power`() {
        assertEquals("-4", formatResult(success("-2^2")))
    }

    @Test
    fun `B02 power is right associative`() {
        assertEquals("512", formatResult(success("2^3^2")))
    }

    @Test
    fun `B03 decimal addition is exact`() {
        assertEquals("0.3", formatResult(success("0.1+0.2")))
    }

    @Test
    fun `B04 division keeps 50 digit working precision`() {
        assertTrue(success("1/3").toPlainString().startsWith("0.3333333333333333333333333333333333"))
    }

    @Test
    fun `B05 factorial of 170 is accepted`() {
        assertTrue(success("170!") > BigDecimal.ZERO)
    }

    @Test
    fun `B06 factorial of 171 is rejected`() {
        assertTrue(failure("171!") is MathError.DomainError)
    }

    @Test
    fun `B07 percent character is the modulo operator`() {
        assertEquals("2", formatResult(success("50%3")))
    }

    // ------------------------------------------------------ 角度制 C02-C04

    @Test
    fun `C02 sine of 30 degrees is one half`() {
        assertEquals("0.5", formatResult(success("sin(30)", EvaluationContext(angleUnit = AngleUnit.DEGREES))))
    }

    @Test
    fun `C03 arcsine restores degree unit`() {
        val value = success("asin(0.5)", EvaluationContext(angleUnit = AngleUnit.DEGREES))
        assertEquals("30", formatResult(value))
    }

    @Test
    fun `C04 arctangent restores degree unit`() {
        val value = success("atan(1)", EvaluationContext(angleUnit = AngleUnit.DEGREES))
        assertEquals("45", formatResult(value))
    }

    @Test
    fun `C05 arcsine keeps radians in radian mode`() {
        val value = success("asin(0.5)", EvaluationContext(angleUnit = AngleUnit.RADIANS))
        assertEquals("0.5235987755982989", formatResult(value))
    }

    // ------------------------------------------------------ 其他边界

    @Test
    fun `D01 sine of pi over six is one half in radians`() {
        assertEquals("0.5", formatResult(success("sin(π/6)")))
    }

    @Test
    fun `D02 nested function calls are evaluated`() {
        assertTrue(formatResult(success("sin(cos(0))")).startsWith("0.84147"))
    }

    @Test
    fun `D03 abs and round compose`() {
        assertEquals("6", formatResult(success("abs(-3)+round(2.5)")))
    }

    @Test
    fun `D04 ans is reusable after a result`() {
        val context = EvaluationContext(ans = BigDecimal("5"))
        assertEquals("20", formatResult(success("ans*4", context)))
    }

    @Test
    fun `D05 arcsine outside unit interval is a domain error`() {
        assertTrue(failure("asin(2)") is MathError.DomainError)
    }

    @Test
    fun `D06 square root of a negative number is undefined`() {
        assertTrue(failure("sqrt(-1)") is MathError.Undefined)
    }

    @Test
    fun `D07 logarithm of zero is a domain error`() {
        assertTrue(failure("log(0)") is MathError.DomainError)
    }

    @Test
    fun `D08 overlong input is rejected before parsing`() {
        assertEquals(MathError.InputTooLong, failure("1".repeat(1001)))
    }

    @Test
    fun `D09 unknown name is a parse error`() {
        assertTrue(failure("foo") is MathError.ParseError)
    }

    @Test
    fun `D10 unsupported character is a parse error`() {
        assertTrue(failure("1#2") is MathError.ParseError)
    }

    @Test
    fun `D11 zero to the zero power is one`() {
        assertEquals("1", formatResult(success("0^0")))
    }

    @Test
    fun `D12 negative base with fractional exponent is undefined`() {
        assertTrue(failure("(-8)^(1/3)") is MathError.Undefined)
    }

    @Test
    fun `D13 factorial of a negative number is a domain error`() {
        assertTrue(failure("(-3)!") is MathError.DomainError)
    }

    @Test
    fun `D14 double negation returns the operand`() {
        assertEquals("1", formatResult(success("--1")))
    }

    @Test
    fun `D14b long unary chain is rejected by the depth budget`() {
        assertEquals(MathError.DepthLimitExceeded, failure("-".repeat(100) + "1"))
    }

    @Test
    fun `D15 nested functions beyond the depth budget are rejected`() {
        val nested = "sin(".repeat(70) + "1" + ")".repeat(70)
        assertEquals(MathError.DepthLimitExceeded, failure(nested))
    }
}