package com.hpcalc.core.math

import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.SymbolicResult
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多项式化简与代入的回归测试，对应 docs/ACCEPTANCE.md 的 D01-D03。 */
class PolynomialCalculatorTest {

    private val calculator = PolynomialCalculator()

    private fun simplified(expression: String): String {
        val result = calculator.simplify(expression)
        assertTrue("expected success for `$expression`, got $result", result is SymbolicResult.Success)
        return (result as SymbolicResult.Success).expression
    }

    private fun substituted(expression: String, values: Map<String, BigDecimal>): String {
        val result = calculator.substitute(expression, values)
        assertTrue("expected success for `$expression`, got $result", result is SymbolicResult.Success)
        return (result as SymbolicResult.Success).expression
    }

    private fun symbolicFailure(expression: String): MathError {
        val result = calculator.simplify(expression)
        assertTrue("expected failure for `$expression`, got $result", result is SymbolicResult.Error)
        return (result as SymbolicResult.Error).error
    }

    @Test
    fun `D01 expands and combines like terms`() {
        assertEquals("x^2 + 3x + 1", simplified("(x+1)^2+x"))
    }

    @Test
    fun `D02 substitution evaluates the polynomial`() {
        assertEquals("11", substituted("(x+1)^2+x", mapOf("x" to BigDecimal(2))))
    }

    @Test
    fun `D03 polynomial division is reported as unsupported`() {
        assertTrue(symbolicFailure("x/x") is MathError.Unsupported)
    }

    @Test
    fun `E01 implicit multiplication between a constant and a variable`() {
        assertEquals("2x", simplified("2x"))
    }

    @Test
    fun `E02 implicit multiplication between parentheses`() {
        assertEquals("x^2 - 1", simplified("(x+1)(x-1)"))
    }

    @Test
    fun `E03 unary minus binds looser than power`() {
        assertEquals("-x^2", simplified("-x^2"))
    }

    @Test
    fun `E04 cancellation removes terms entirely`() {
        assertEquals("0", simplified("x-x"))
    }

    @Test
    fun `E05 missing substitution value is reported`() {
        val result = calculator.substitute("x+y", mapOf("x" to BigDecimal.ONE))
        assertTrue(result is SymbolicResult.Error)
    }

    @Test
    fun `E06 ans can be used as a constant`() {
        calculator.setAns(BigDecimal(3))
        assertEquals("3x", simplified("ans*x"))
    }

    @Test
    fun `E07 ans without a previous result is reported`() {
        val fresh = PolynomialCalculator()
        val result = fresh.simplify("ans*x")
        assertTrue(result is SymbolicResult.Error)
        assertTrue((result as SymbolicResult.Error).error is MathError.NotAvailable)
    }

    @Test
    fun `E08 deep nesting is rejected instead of overflowing`() {
        val nested = "(".repeat(70) + "x" + ")".repeat(70)
        assertEquals(MathError.DepthLimitExceeded, symbolicFailure(nested))
    }

    @Test
    fun `E09 multi variable expansion keeps every monomial`() {
        assertEquals("x^2 + 2xy + y^2", simplified("(x+y)^2"))
    }

    @Test
    fun `E10 trailing operator is reported as a parse error`() {
        assertTrue(symbolicFailure("x+") is MathError.ParseError)
    }
}