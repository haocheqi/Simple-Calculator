package com.hpcalc.core.math

import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 精确有理数求值器：只承诺能精确的东西，其余明确回退。 */
class RationalEvaluatorTest {

    private val evaluator = RationalEvaluator()

    private fun exact(expression: String): RationalResult.Exact {
        val result = evaluator.evaluate(expression)
        assertTrue("expected an exact result for `$expression`, got $result", result is RationalResult.Exact)
        return result as RationalResult.Exact
    }

    private fun failure(expression: String): MathError {
        val result = evaluator.evaluate(expression)
        assertTrue("expected a failure for `$expression`, got $result", result is RationalResult.Error)
        return (result as RationalResult.Error).error
    }

    @Test
    fun `simple fraction`() {
        val result = exact("1/3")
        assertEquals(BigInteger.ONE, result.numerator)
        assertEquals(BigInteger.valueOf(3), result.denominator)
    }

    @Test
    fun `fraction is reduced`() {
        val result = exact("2/4")
        assertEquals(BigInteger.ONE, result.numerator)
        assertEquals(BigInteger.valueOf(2), result.denominator)
    }

    @Test
    fun `decimal literal becomes an exact fraction`() {
        val result = exact("0.25")
        assertEquals(BigInteger.ONE, result.numerator)
        assertEquals(BigInteger.valueOf(4), result.denominator)
    }

    @Test
    fun `decimal addition stays exact`() {
        // 浮点下 0.1 + 0.2 != 0.3，这里必须是精确的 3/10。
        val result = exact("0.1 + 0.2")
        assertEquals(BigInteger.valueOf(3), result.numerator)
        assertEquals(BigInteger.valueOf(10), result.denominator)
    }

    @Test
    fun `scientific literal becomes an integer`() {
        val result = exact("1e3")
        assertEquals(BigInteger.valueOf(1000), result.numerator)
        assertTrue(result.isInteger)
    }

    @Test
    fun `fractions add with a common denominator`() {
        val result = exact("1/3 + 1/6")
        assertEquals(BigInteger.ONE, result.numerator)
        assertEquals(BigInteger.valueOf(2), result.denominator)
    }

    @Test
    fun `fraction raised to a power`() {
        val result = exact("(2/3)^2")
        assertEquals(BigInteger.valueOf(4), result.numerator)
        assertEquals(BigInteger.valueOf(9), result.denominator)
    }

    @Test
    fun `negative exponent inverts`() {
        val result = exact("2^-1")
        assertEquals(BigInteger.ONE, result.numerator)
        assertEquals(BigInteger.valueOf(2), result.denominator)
    }

    @Test
    fun `negative denominator is normalised`() {
        val result = exact("1/-3")
        assertEquals(BigInteger.valueOf(-1), result.numerator)
        assertEquals(BigInteger.valueOf(3), result.denominator)
    }

    @Test
    fun `unary minus binds looser than the power`() {
        assertEquals(BigInteger.valueOf(-4), exact("-2^2").numerator)
    }

    @Test
    fun `power is right associative`() {
        assertEquals(BigInteger.valueOf(512), exact("2^3^2").numerator)
    }

    @Test
    fun `integer division collapses to an integer`() {
        val result = exact("4/2")
        assertTrue(result.isInteger)
        assertEquals(BigInteger.valueOf(2), result.numerator)
    }

    @Test
    fun `division by zero is an error`() {
        assertEquals(MathError.DivisionByZero, failure("1/0"))
    }

    @Test
    fun `transcendental functions are not exact`() {
        assertTrue(failure("sin(1)") is MathError.Unsupported)
    }

    @Test
    fun `constants are not exact`() {
        assertTrue(failure("π") is MathError.Unsupported)
    }

    @Test
    fun `ans is not exact`() {
        assertTrue(failure("ans*2") is MathError.Unsupported)
    }

    @Test
    fun `fractional exponent is not exact`() {
        assertTrue(failure("9^0.5") is MathError.Unsupported)
    }

    @Test
    fun `factorial is not handled here`() {
        assertTrue(failure("5!") is MathError.Unsupported)
    }

    @Test
    fun `modulo is not handled here`() {
        assertTrue(failure("5%3") is MathError.Unsupported)
    }

    @Test
    fun `nesting beyond the limit is rejected`() {
        val deep = "(".repeat(100) + "1" + ")".repeat(100)
        assertEquals(MathError.DepthLimitExceeded, failure(deep))
    }

    @Test
    fun `integer renders as a plain number block`() {
        assertEquals(MathBlock.Number("2"), exact("4/2").toMathBlock())
    }

    @Test
    fun `fraction renders as a fraction block`() {
        val block = exact("1/3").toMathBlock() as MathBlock.Fraction
        assertEquals(MathBlock.Number("1"), block.numerator)
        assertEquals(MathBlock.Number("3"), block.denominator)
    }

    @Test
    fun `exact powers that would explode are refused`() {
        // 精确幂没有精度上限，嵌套幂可以要求几亿位数字。必须提前止损，
        // 否则「先试一次精确计算」会在每次求值时把界面卡死。
        val error = failure("(1000^10000)^10000")
        assertTrue("expected ArgumentTooLarge, got $error", error is MathError.DomainError)
        assertEquals(MathIssue.ArgumentTooLarge, (error as MathError.DomainError).issue)
    }

    @Test
    fun `modest exact powers still work`() {
        // 止损线不能低到影响正常使用。
        assertEquals(BigInteger.valueOf(1024), exact("2^10").numerator)
        assertEquals(302, exact("2^1000").numerator.toString().length)
    }
}
