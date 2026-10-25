package com.hpcalc.core.math

import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.Matrix
import com.hpcalc.core.model.MatrixOperation
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 求值分派层的回归测试。
 *
 * 这一层的职责是「模式 + 输入 → MathBlock + 新 ans」，
 * 是界面唯一的结果来源，所以每种模式的分支都要有用例。
 */
class CalculatorEngineTest {

    private val engine = CalculatorEngine()
    private val context = EvaluationContext()

    private fun number(block: MathBlock): String = (block as MathBlock.Number).literal

    @Test
    fun `numeric success yields a number block and the new ans`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "1+1", context, DisplayMode.DECIMAL)
        assertTrue(outcome.succeeded)
        assertEquals("2", number(outcome.block))
        assertEquals(BigDecimal(2), outcome.value)
    }

    @Test
    fun `numeric failure yields an error block and keeps ans`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "1/0", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertEquals(MathError.DivisionByZero, (outcome.block as MathBlock.Error).error)
        assertNull(outcome.value)
    }

    @Test
    fun `blank input is not evaluated`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "   ", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertNull(outcome.value)
    }

    @Test
    fun `scientific mode uses the numeric evaluator`() {
        val degrees = EvaluationContext(angleUnit = com.hpcalc.core.model.AngleUnit.DEGREES)
        val outcome = engine.evaluate(CalculatorMode.SCIENTIFIC, "sin(30)", degrees, DisplayMode.DECIMAL)
        assertEquals("0.5", number(outcome.block))
    }

    @Test
    fun `symbolic mode yields a text block`() {
        val outcome = engine.evaluate(CalculatorMode.SYMBOLIC, "(x+1)^2", context, DisplayMode.DECIMAL)
        assertTrue(outcome.succeeded)
        assertEquals("x^2 + 2x + 1", (outcome.block as MathBlock.Text).value)
        assertNull(outcome.value)
    }

    @Test
    fun `symbolic constant result feeds ans`() {
        val outcome = engine.evaluate(CalculatorMode.SYMBOLIC, "2+3", context, DisplayMode.DECIMAL)
        assertTrue(outcome.succeeded)
        assertEquals(BigDecimal(5), outcome.value)
    }

    @Test
    fun `symbolic failure yields an error block`() {
        val outcome = engine.evaluate(CalculatorMode.SYMBOLIC, "x/x", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertTrue((outcome.block as MathBlock.Error).error is MathError.Unsupported)
    }

    @Test
    fun `ans from the context reaches the symbolic evaluator`() {
        val outcome = engine.evaluate(
            CalculatorMode.SYMBOLIC,
            "ans*x",
            EvaluationContext(ans = BigDecimal(4)),
            DisplayMode.DECIMAL
        )
        assertEquals("4x", (outcome.block as MathBlock.Text).value)
    }

    @Test
    fun `matrix mode is rejected on the expression path`() {
        val outcome = engine.evaluate(CalculatorMode.MATRIX, "1+1", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertTrue((outcome.block as MathBlock.Error).error is MathError.Unsupported)
    }

    @Test
    fun `matrix multiplication yields a matrix block`() {
        val left = Matrix(
            listOf(listOf(BigDecimal.ONE, BigDecimal(2)), listOf(BigDecimal(3), BigDecimal(4)))
        )
        val right = Matrix(
            listOf(listOf(BigDecimal(5), BigDecimal(6)), listOf(BigDecimal(7), BigDecimal(8)))
        )
        val outcome = engine.matrix(MatrixOperation.Multiply(left, right), DisplayMode.DECIMAL)

        assertTrue(outcome.succeeded)
        val block = outcome.block as MathBlock.Matrix
        assertEquals("19", (block.rows[0][0] as MathBlock.Number).literal)
        assertEquals("22", (block.rows[0][1] as MathBlock.Number).literal)
        assertEquals(MathBlock.Delimiter.PAREN, block.delimiter)
    }

    @Test
    fun `matrix scalar result becomes a number block`() {
        val matrix = Matrix(
            listOf(listOf(BigDecimal.ONE, BigDecimal(2)), listOf(BigDecimal(3), BigDecimal(4)))
        )
        val outcome = engine.matrix(MatrixOperation.Determinant(matrix), DisplayMode.DECIMAL)
        assertEquals("-2", number(outcome.block))
        assertEquals(BigDecimal(-2), outcome.value)
    }

    @Test
    fun `matrix rank result becomes a text block`() {
        val matrix = Matrix(
            listOf(listOf(BigDecimal.ONE, BigDecimal(2)), listOf(BigDecimal(2), BigDecimal(4)))
        )
        val outcome = engine.matrix(MatrixOperation.Rank(matrix), DisplayMode.DECIMAL)
        assertEquals("1", (outcome.block as MathBlock.Text).value)
    }

    @Test
    fun `engineering mode always switches notation`() {
        val decimal = engine.evaluate(CalculatorMode.BASIC, "12345678.9", context, DisplayMode.DECIMAL)
        val engineering = engine.evaluate(CalculatorMode.BASIC, "12345678.9", context, DisplayMode.ENGINEERING)

        assertEquals("12345678.9", number(decimal.block))
        assertTrue(number(engineering.block).contains("E"))
    }

    @Test
    fun `exact fraction mode renders a fraction block`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "1/3", context, DisplayMode.EXACT_FRACTION)
        assertTrue(outcome.succeeded)
        val block = outcome.block as MathBlock.Fraction
        assertEquals(MathBlock.Number("1"), block.numerator)
        assertEquals(MathBlock.Number("3"), block.denominator)
    }

    @Test
    fun `exact fraction mode collapses whole numbers`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "4/2", context, DisplayMode.EXACT_FRACTION)
        assertEquals(MathBlock.Number("2"), outcome.block)
        assertEquals(BigDecimal(2), outcome.value)
    }

    @Test
    fun `exact fraction mode falls back for irrational results`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "sin(1)", context, DisplayMode.EXACT_FRACTION)
        assertTrue(outcome.succeeded)
        assertTrue(outcome.block is MathBlock.Number)
    }

    @Test
    fun `exact fraction mode still reports real errors`() {
        val outcome = engine.evaluate(CalculatorMode.BASIC, "1/0", context, DisplayMode.EXACT_FRACTION)
        assertFalse(outcome.succeeded)
        assertEquals(MathError.DivisionByZero, (outcome.block as MathBlock.Error).error)
    }

    @Test
    fun `substitution evaluates a polynomial at a value`() {
        val outcome = engine.substitute("(x+1)^2+x", "x", "2", context, DisplayMode.DECIMAL)
        assertTrue(outcome.succeeded)
        assertEquals("11", number(outcome.block))
        assertEquals(BigDecimal(11), outcome.value)
    }

    @Test
    fun `substitution accepts an expression as the value`() {
        val outcome = engine.substitute("x^2", "x", "3+1", context, DisplayMode.DECIMAL)
        assertEquals("16", number(outcome.block))
    }

    @Test
    fun `substitution honours the angle unit of the value`() {
        val degrees = EvaluationContext(angleUnit = com.hpcalc.core.model.AngleUnit.DEGREES)
        val outcome = engine.substitute("x", "x", "sin(30)", degrees, DisplayMode.DECIMAL)
        assertEquals("0.5", number(outcome.block))
    }

    @Test
    fun `substitution reports a variable without a value`() {
        val outcome = engine.substitute("x+y", "x", "1", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertTrue((outcome.block as MathBlock.Error).error is MathError.DomainError)
    }

    @Test
    fun `substitution rejects an invalid variable name`() {
        val outcome = engine.substitute("x", "1x", "2", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertTrue((outcome.block as MathBlock.Error).error is MathError.ParseError)
    }

    @Test
    fun `substitution requires a value`() {
        val outcome = engine.substitute("x+1", "x", "   ", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertTrue((outcome.block as MathBlock.Error).error is MathError.ParseError)
    }

    @Test
    fun `substitution of a blank expression yields nothing`() {
        val outcome = engine.substitute("  ", "x", "1", context, DisplayMode.DECIMAL)
        assertFalse(outcome.succeeded)
        assertNull(outcome.value)
    }
}