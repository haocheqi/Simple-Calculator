package com.hpcalc.core.math

import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.Matrix
import com.hpcalc.core.model.MatrixEvaluator
import com.hpcalc.core.model.MatrixOperation
import com.hpcalc.core.model.MatrixResult
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 实数矩阵运算，全程 `BigDecimal`，默认 50 位精度。
 *
 * 相对 v2.0 的变更：
 * - 支持任意 `rows × cols`（各自 1..6），v2.0 的界面把能力锁成了 `n×n`。
 * - 求秩的零判定从**绝对**阈值 `1e-40` 改为**相对**阈值（相对矩阵元素最大绝对值），
 *   修正大数值矩阵把满秩误判为降秩的问题。
 * - 错误统一为 [MathError]，不再依赖 `require` 的字符串异常。
 */
class MatrixCalculator(
    private val mc: MathContext = MathContext(50, RoundingMode.HALF_UP)
) : MatrixEvaluator {

    private class MatrixException(val error: MathError) : RuntimeException(error.message)

    override fun evaluate(operation: MatrixOperation): MatrixResult = try {
        when (operation) {
            is MatrixOperation.Add -> elementwise(operation.left, operation.right) { a, b -> a.add(b, mc) }
            is MatrixOperation.Subtract -> elementwise(operation.left, operation.right) { a, b -> a.subtract(b, mc) }
            is MatrixOperation.Multiply -> multiply(operation.left, operation.right)
            is MatrixOperation.ScalarMultiply -> MatrixResult.Success(
                Matrix(operation.matrix.rows.map { row -> row.map { it.multiply(operation.scalar, mc) } })
            )
            is MatrixOperation.Transpose -> MatrixResult.Success(operation.matrix.transpose())
            is MatrixOperation.Determinant -> MatrixResult.Scalar(determinant(operation.matrix))
            is MatrixOperation.Inverse -> inverse(operation.matrix)
            is MatrixOperation.Rank -> MatrixResult.Text(rank(operation.matrix).toString())
        }
    } catch (e: MatrixException) {
        MatrixResult.Error(e.error)
    } catch (e: Exception) {
        MatrixResult.Error(MathError.DimensionMismatch(e.message ?: "invalid matrix"))
    }

    private fun elementwise(
        left: Matrix,
        right: Matrix,
        combine: (BigDecimal, BigDecimal) -> BigDecimal
    ): MatrixResult {
        if (left.rowCount != right.rowCount || left.columnCount != right.columnCount) {
            fail(MathError.DimensionMismatch("both operands must be ${left.rowCount}×${left.columnCount}"))
        }
        return MatrixResult.Success(
            Matrix(left.rows.zip(right.rows) { leftRow, rightRow -> leftRow.zip(rightRow, combine) })
        )
    }

    private fun multiply(left: Matrix, right: Matrix): MatrixResult {
        if (left.columnCount != right.rowCount) {
            fail(MathError.DimensionMismatch("left has ${left.columnCount} columns, right has ${right.rowCount} rows"))
        }
        val product = Matrix(
            (0 until left.rowCount).map { r ->
                (0 until right.columnCount).map { c ->
                    (0 until left.columnCount).fold(BigDecimal.ZERO) { sum, k ->
                        sum.add(left.rows[r][k].multiply(right.rows[k][c], mc), mc)
                    }
                }
            }
        )
        return MatrixResult.Success(product)
    }

    private fun determinant(matrix: Matrix): BigDecimal {
        if (!matrix.isSquare) fail(MathError.DimensionMismatch("determinant needs a square matrix"))
        return determinantUnchecked(matrix)
    }

    private fun determinantUnchecked(matrix: Matrix): BigDecimal {
        when (matrix.rowCount) {
            1 -> return matrix.rows[0][0]
            2 -> return matrix.rows[0][0].multiply(matrix.rows[1][1], mc)
                .subtract(matrix.rows[0][1].multiply(matrix.rows[1][0], mc), mc)
        }
        return (0 until matrix.columnCount).fold(BigDecimal.ZERO) { sum, col ->
            val sign = if (col % 2 == 0) BigDecimal.ONE else BigDecimal.ONE.negate()
            val term = sign.multiply(matrix.rows[0][col], mc)
                .multiply(determinantUnchecked(minor(matrix, 0, col)), mc)
            sum.add(term, mc)
        }
    }

    private fun minor(matrix: Matrix, row: Int, col: Int): Matrix =
        Matrix(
            matrix.rows.filterIndexed { r, _ -> r != row }
                .map { it.filterIndexed { c, _ -> c != col } }
        )

    private fun inverse(matrix: Matrix): MatrixResult {
        if (!matrix.isSquare) fail(MathError.DimensionMismatch("inverse needs a square matrix"))
        val n = matrix.rowCount
        val augmented = Array(n) { r ->
            Array(2 * n) { c ->
                when {
                    c < n -> matrix.rows[r][c]
                    c - n == r -> BigDecimal.ONE
                    else -> BigDecimal.ZERO
                }
            }
        }
        for (i in 0 until n) {
            var pivot = i
            while (pivot < n && augmented[pivot][i].signum() == 0) pivot++
            if (pivot >= n) fail(MathError.SingularMatrix)
            val swap = augmented[i]
            augmented[i] = augmented[pivot]
            augmented[pivot] = swap
            val divisor = augmented[i][i]
            for (c in 0 until 2 * n) augmented[i][c] = augmented[i][c].divide(divisor, mc)
            for (r in 0 until n) {
                if (r == i) continue
                val factor = augmented[r][i]
                if (factor.signum() == 0) continue
                for (c in 0 until 2 * n) {
                    augmented[r][c] = augmented[r][c].subtract(factor.multiply(augmented[i][c], mc), mc)
                }
            }
        }
        return MatrixResult.Success(
            Matrix((0 until n).map { r -> (0 until n).map { c -> augmented[r][c + n] } })
        )
    }

    private fun rank(matrix: Matrix): Int {
        val working = matrix.rows.map { it.toMutableList() }.toMutableList()
        // 相对容差：以矩阵元素的最大绝对值为尺度，避免大数值矩阵被误判为降秩。
        val scale = matrix.rows.asSequence().flatten().maxOfOrNull { it.abs() } ?: BigDecimal.ZERO
        val tolerance = scale.multiply(RANK_RELATIVE_TOLERANCE)

        var rank = 0
        var col = 0
        while (col < matrix.columnCount && rank < matrix.rowCount) {
            var pivot = rank
            while (pivot < matrix.rowCount && working[pivot][col].abs() <= tolerance) pivot++
            if (pivot < matrix.rowCount) {
                val swap = working[rank]
                working[rank] = working[pivot]
                working[pivot] = swap
                for (r in rank + 1 until matrix.rowCount) {
                    val factor = working[r][col].divide(working[rank][col], mc)
                    for (c in col until matrix.columnCount) {
                        working[r][c] = working[r][c].subtract(factor.multiply(working[rank][c], mc), mc)
                    }
                }
                rank++
            }
            col++
        }
        return rank
    }

    private fun fail(error: MathError): Nothing = throw MatrixException(error)

    companion object {
        /** 相对容差：以最大元素绝对值为 1 个单位，小于 1e-40 视为零。 */
        val RANK_RELATIVE_TOLERANCE: BigDecimal = BigDecimal("1e-40")
    }
}