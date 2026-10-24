package com.example.calculator

import com.example.calculator.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorCoreTest {
    private val numeric = HighPrecisionEvaluator()
    @Test fun highPrecisionDivision() { val r = numeric.evaluate("1/3"); assertTrue(r is EvaluationResult.Success); assertTrue((r as EvaluationResult.Success).value.toPlainString().startsWith("0.333333333333333333")) }
    @Test fun trigConstant() { val r = numeric.evaluate("sin(π/6)"); assertTrue(r is EvaluationResult.Success); assertEquals("0.5", formatResult((r as EvaluationResult.Success).value)) }
    @Test fun polynomialSimplification() { assertEquals("x^2 + 3x + 1", (PolynomialCalculator().simplify("(x+1)^2+x") as SymbolicResult.Success).expression) }
    @Test fun polynomialSubstitution() { assertEquals("11", (PolynomialCalculator().substitute("(x+1)^2+x", mapOf("x" to BigDecimal(2))) as SymbolicResult.Success).expression) }
    @Test fun determinant() { val m=BigDecimalMatrix(listOf(listOf(BigDecimal.ONE,BigDecimal(2)),listOf(BigDecimal(3),BigDecimal(4)))); val r=MatrixCalculator().evaluate(MatrixOperation.Determinant(m)); assertEquals(BigDecimal("-2"),(r as MatrixResult.Scalar).value) }
    @Test fun matrixMultiply() { val a=BigDecimalMatrix(listOf(listOf(BigDecimal.ONE,BigDecimal(2))));val b=BigDecimalMatrix(listOf(listOf(BigDecimal(3)),listOf(BigDecimal(4))));val r=MatrixCalculator().evaluate(MatrixOperation.Multiply(a,b)) as MatrixResult.Success;assertEquals(BigDecimal("11"),r.matrix.rows[0][0]) }
    @Test fun ansCanBeReused() { val first = numeric.evaluate("2+3"); val value = (first as EvaluationResult.Success).value; val second = numeric.evaluate("ans*4", EvaluationContext(ans = value)); assertEquals(BigDecimal("20"), (second as EvaluationResult.Success).value) }
    @Test fun matrixAdd() { val a=BigDecimalMatrix(listOf(listOf(BigDecimal.ONE,BigDecimal(2)))); val b=BigDecimalMatrix(listOf(listOf(BigDecimal(3),BigDecimal(4)))); val r=MatrixCalculator().evaluate(MatrixOperation.Add(a,b)) as MatrixResult.Success; assertEquals(BigDecimal("4"),r.matrix.rows[0][0]); assertEquals(BigDecimal("6"),r.matrix.rows[0][1]) }
}
