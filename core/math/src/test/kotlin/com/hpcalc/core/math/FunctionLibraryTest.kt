package com.hpcalc.core.math

import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.EvaluationResult
import com.hpcalc.core.model.MathError
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 3 函数库的回归测试：数论、统计、极值、根、对数底、角度换算。 */
class FunctionLibraryTest {

    private val evaluator = HighPrecisionEvaluator()

    private fun value(expression: String, context: EvaluationContext = EvaluationContext()): String {
        val result = evaluator.evaluate(expression, context)
        assertTrue("expected success for `$expression`, got $result", result is EvaluationResult.Success)
        return formatResult((result as EvaluationResult.Success).value)
    }

    private fun failure(expression: String): MathError {
        val result = evaluator.evaluate(expression)
        assertTrue("expected failure for `$expression`, got $result", result is EvaluationResult.Error)
        return (result as EvaluationResult.Error).error
    }

    @Test
    fun `gcd of two integers`() {
        assertEquals("6", value("gcd(48, 18)"))
    }

    @Test
    fun `lcm of two integers`() {
        assertEquals("12", value("lcm(4, 6)"))
    }

    @Test
    fun `lcm with zero is zero`() {
        assertEquals("0", value("lcm(0, 5)"))
    }

    @Test
    fun `gcd rejects non integers`() {
        assertTrue(failure("gcd(1.5, 2)") is MathError.DomainError)
    }

    @Test
    fun `binomial coefficient`() {
        assertEquals("10", value("comb(5, 2)"))
    }

    @Test
    fun `binomial coefficient out of range is zero`() {
        assertEquals("0", value("comb(5, 7)"))
    }

    @Test
    fun `permutation count`() {
        assertEquals("20", value("perm(5, 2)"))
    }

    @Test
    fun `min of several values`() {
        assertEquals("1", value("min(3, 1, 2)"))
    }

    @Test
    fun `max of several values`() {
        assertEquals("3", value("max(3, 1, 2)"))
    }

    @Test
    fun `min without arguments is a parse error`() {
        assertTrue(failure("min()") is MathError.ParseError)
    }

    @Test
    fun `clamp keeps the value inside the range`() {
        assertEquals("3", value("clamp(5, 0, 3)"))
    }

    @Test
    fun `clamp rejects an inverted range`() {
        assertTrue(failure("clamp(1, 5, 0)") is MathError.DomainError)
    }

    @Test
    fun `sum of a list`() {
        assertEquals("6", value("sum(1, 2, 3)"))
    }

    @Test
    fun `mean of a list`() {
        assertEquals("2.5", value("mean(1, 2, 3, 4)"))
    }

    @Test
    fun `median of an odd count`() {
        assertEquals("2", value("median(1, 3, 2)"))
    }

    @Test
    fun `median of an even count`() {
        assertEquals("2.5", value("median(1, 2, 3, 4)"))
    }

    @Test
    fun `sample standard deviation`() {
        // sqrt(32 / (8 - 1)) = sqrt(32/7)
        assertEquals("2.138089935299395", value("stdev(2,4,4,4,5,5,7,9)"))
    }

    @Test
    fun `stdev needs at least two values`() {
        assertTrue(failure("stdev(1)") is MathError.DomainError)
    }

    @Test
    fun `cube root of a negative number`() {
        assertEquals("-3", value("cbrt(-27)"))
    }

    @Test
    fun `nth root of a positive number`() {
        assertEquals("3", value("root(27, 3)"))
    }

    @Test
    fun `even root of a negative number is undefined`() {
        assertTrue(failure("root(-16, 2)") is MathError.Undefined)
    }

    @Test
    fun `logarithm with an explicit base`() {
        assertEquals("3", value("logb(2, 8)"))
    }

    @Test
    fun `logarithm base one is a domain error`() {
        assertTrue(failure("logb(1, 8)") is MathError.DomainError)
    }

    @Test
    fun `atan2 in radians`() {
        assertTrue(value("atan2(1, 0)").startsWith("1.5707"))
    }

    @Test
    fun `atan2 honours the degree unit`() {
        assertEquals("45", value("atan2(1, 1)", EvaluationContext(angleUnit = AngleUnit.DEGREES)))
    }

    @Test
    fun `atan2 of the origin is a domain error`() {
        assertTrue(failure("atan2(0, 0)") is MathError.DomainError)
    }

    @Test
    fun `deg converts radians to degrees`() {
        assertEquals("180", value("deg(π)"))
    }

    @Test
    fun `rad converts degrees to radians`() {
        assertTrue(value("rad(180)").startsWith("3.14159"))
    }

    @Test
    fun `wrong argument count is a parse error`() {
        assertTrue(failure("sin(1, 2)") is MathError.ParseError)
    }

    @Test
    fun `a comma outside a call is rejected`() {
        assertTrue(failure("1,5") is MathError.ParseError)
    }

    @Test
    fun `functions nest with multi argument calls`() {
        assertEquals("2", value("logb(2, max(3, 4))"))
    }

    @Test
    fun `old single argument functions still work`() {
        assertEquals(BigDecimal("7").toPlainString(), value("sqrt(49)"))
    }
}