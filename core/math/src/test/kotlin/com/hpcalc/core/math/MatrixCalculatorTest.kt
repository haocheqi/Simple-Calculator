package com.hpcalc.core.math

import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.Matrix
import com.hpcalc.core.model.MatrixOperation
import com.hpcalc.core.model.MatrixResult
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 矩阵运算回归测试，对应 docs/ACCEPTANCE.md 的 E01-E05。 */
class MatrixCalculatorTest {

    private val calculator = MatrixCalculator()

    private fun matrixOf(vararg rows: List<Int>): Matrix =
        Matrix(rows.map { row -> row.map { BigDecimal(it) } })

    private fun success(operation: MatrixOperation): Matrix {
        val result = calculator.evaluate(operation)
        assertTrue("expected success, got $result", result is MatrixResult.Success)
        return (result as MatrixResult.Success).matrix
    }

    private fun failure(operation: MatrixOperation): MathError {
        val result = calculator.evaluate(operation)
        assertTrue("expected failure, got $result", result is MatrixResult.Error)
        return (result as MatrixResult.Error).error
    }

    private fun assertClose(expected: String, actual: BigDecimal, tolerance: String = "1e-40") {
        val diff = actual.subtract(BigDecimal(expected)).abs()
        assertTrue("expected $expected, got $actual", diff <= BigDecimal(tolerance))
    }

    @Test
    fun `E01 multiplies a 2x3 by a 3x2`() {
        val left = matrixOf(listOf(1, 2, 3), listOf(4, 5, 6))
        val right = matrixOf(listOf(7, 8), listOf(9, 10), listOf(11, 12))
        val product = success(MatrixOperation.Multiply(left, right))

        assertEquals(2, product.rowCount)
        assertEquals(2, product.columnCount)
        assertEquals(BigDecimal(58), product.rows[0][0])
        assertEquals(BigDecimal(64), product.rows[0][1])
        assertEquals(BigDecimal(139), product.rows[1][0])
        assertEquals(BigDecimal(154), product.rows[1][1])
    }

    @Test
    fun `E02 singular matrix has no inverse`() {
        val singular = matrixOf(listOf(1, 2), listOf(2, 4))
        assertEquals(MathError.SingularMatrix, failure(MatrixOperation.Inverse(singular)))
    }

    @Test
    fun `E03 determinant of a non square matrix is rejected`() {
        val rectangular = matrixOf(listOf(1, 2, 3), listOf(4, 5, 6))
        assertTrue(failure(MatrixOperation.Determinant(rectangular)) is MathError.DimensionMismatch)
    }

    @Test
    fun `E04 inverse multiplied by the original yields the identity`() {
        val original = matrixOf(listOf(1, 2, 3), listOf(0, 1, 4), listOf(5, 6, 0))
        val inverse = success(MatrixOperation.Inverse(original))
        val product = success(MatrixOperation.Multiply(inverse, original))

        for (r in 0 until 3) {
            for (c in 0 until 3) {
                assertClose(if (r == c) "1" else "0", product.rows[r][c])
            }
        }
    }

    @Test
    fun `E05 determinant of a 2x2`() {
        val matrix = matrixOf(listOf(1, 2), listOf(3, 4))
        val result = calculator.evaluate(MatrixOperation.Determinant(matrix))
        assertTrue(result is MatrixResult.Scalar)
        assertEquals(BigDecimal(-2), (result as MatrixResult.Scalar).value)
    }

    @Test
    fun `F01 addition requires matching dimensions`() {
        val left = matrixOf(listOf(1, 2))
        val right = matrixOf(listOf(1, 2), listOf(3, 4))
        assertTrue(failure(MatrixOperation.Add(left, right)) is MathError.DimensionMismatch)
    }

    @Test
    fun `F02 addition of matching matrices is elementwise`() {
        val sum = success(MatrixOperation.Add(matrixOf(listOf(1, 2)), matrixOf(listOf(3, 4))))
        assertEquals(BigDecimal(4), sum.rows[0][0])
        assertEquals(BigDecimal(6), sum.rows[0][1])
    }

    @Test
    fun `F03 transpose swaps the dimensions`() {
        val transposed = success(MatrixOperation.Transpose(matrixOf(listOf(1, 2, 3), listOf(4, 5, 6))))
        assertEquals(3, transposed.rowCount)
        assertEquals(2, transposed.columnCount)
        assertEquals(BigDecimal(4), transposed.rows[0][1])
    }

    @Test
    fun `F04 rank of a rank deficient matrix`() {
        val result = calculator.evaluate(MatrixOperation.Rank(matrixOf(listOf(1, 2, 3), listOf(2, 4, 6))))
        assertEquals("1", (result as MatrixResult.Text).value)
    }

    @Test
    fun `F05 rank is scale invariant`() {
        // v2.0 用绝对阈值 1e-40，这个极小的满秩矩阵会被误判为降秩。
        val tiny = Matrix(
            listOf(
                listOf(BigDecimal("1e-50"), BigDecimal.ZERO),
                listOf(BigDecimal.ZERO, BigDecimal("1e-50"))
            )
        )
        val result = calculator.evaluate(MatrixOperation.Rank(tiny))
        assertEquals("2", (result as MatrixResult.Text).value)
    }

    @Test
    fun `F06 scalar multiplication`() {
        val scaled = success(MatrixOperation.ScalarMultiply(matrixOf(listOf(1, 2)), BigDecimal(3)))
        assertEquals(BigDecimal(3), scaled.rows[0][0])
        assertEquals(BigDecimal(6), scaled.rows[0][1])
    }

    @Test
    fun `F07 multiplying incompatible shapes is rejected`() {
        val left = matrixOf(listOf(1, 2))
        val right = matrixOf(listOf(1, 2))
        assertTrue(failure(MatrixOperation.Multiply(left, right)) is MathError.DimensionMismatch)
    }

    @Test
    fun `F08 determinant of a 6x6 completes`() {
        val six = Matrix(
            (0 until 6).map { r -> (0 until 6).map { c -> BigDecimal(if (r == c) 2 else 1) } }
        )
        val result = calculator.evaluate(MatrixOperation.Determinant(six))
        assertTrue(result is MatrixResult.Scalar)
    }
}