package com.hpcalc.core.math

import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import com.hpcalc.core.model.Matrix
import com.hpcalc.core.model.MatrixEvaluator
import com.hpcalc.core.model.MatrixOperation
import com.hpcalc.core.model.MatrixResult
import java.math.BigDecimal
import java.math.BigInteger
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
            fail(MathError.DimensionMismatch("both operands must be ${left.rowCount}×${left.columnCount}", MathIssue.MatrixShapeMismatch))
        }
        return MatrixResult.Success(
            Matrix(left.rows.zip(right.rows) { leftRow, rightRow -> leftRow.zip(rightRow, combine) })
        )
    }

    private fun multiply(left: Matrix, right: Matrix): MatrixResult {
        if (left.columnCount != right.rowCount) {
            fail(MathError.DimensionMismatch("left has ${left.columnCount} columns, right has ${right.rowCount} rows", MathIssue.MatrixShapeMismatch))
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
        if (!matrix.isSquare) fail(MathError.DimensionMismatch("determinant needs a square matrix", MathIssue.NeedsSquareMatrix))
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
        if (!matrix.isSquare) fail(MathError.DimensionMismatch("inverse needs a square matrix", MathIssue.NeedsSquareMatrix))
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
            Matrix((0 until n).map { r -> (0 until n).map { c -> augmented[r][c + n] } }),
            exact = exactInverseBlock(matrix)
        )
    }

    /**
     * 精确逆：用有理数（[Frac]）而不是 `BigDecimal` 做高斯-约当消元。
     *
     * 整数矩阵的逆几乎总是分数，`1/3` 用 50 位小数写成 `0.3333…` 既不准也难读；
     * 真分式才是这个工具该给的结果。
     *
     * 返回 null 的两种情况：数值上已判定奇异（交给小数路径报错），或分数膨胀到没有可读性
     * （输入小数位数太深时，分母会长到几百位）。
     */
    private fun exactInverseBlock(matrix: Matrix): MathBlock? {
        val n = matrix.rowCount
        if (n == 0) return null
        for (r in 0 until n) {
            for (c in 0 until n) {
                val scale = matrix.rows[r][c].stripTrailingZeros().scale()
                if (scale > MAX_EXACT_SCALE) return null
            }
        }

        val a = MutableList(n) { r ->
            MutableList(2 * n) { c ->
                when {
                    c < n -> Frac.of(matrix.rows[r][c])
                    c - n == r -> Frac.ONE
                    else -> Frac.ZERO
                }
            }
        }

        for (i in 0 until n) {
            var pivot = i
            while (pivot < n && a[pivot][i].isZero) pivot++
            if (pivot >= n) return null
            if (pivot != i) {
                val swap = a[i]
                a[i] = a[pivot]
                a[pivot] = swap
            }
            val divisor = a[i][i]
            for (c in 0 until 2 * n) a[i][c] = a[i][c] / divisor
            for (r in 0 until n) {
                if (r == i) continue
                val factor = a[r][i]
                if (factor.isZero) continue
                for (c in 0 until 2 * n) a[r][c] = a[r][c] - factor * a[i][c]
            }
        }

        val rows = ArrayList<List<MathBlock>>(n)
        for (r in 0 until n) {
            val cells = ArrayList<MathBlock>(n)
            for (c in 0 until n) {
                val cell = a[r][c + n]
                if (cell.digits > MAX_EXACT_DIGITS) return null
                cells.add(cell.toBlock())
            }
            rows.add(cells)
        }
        return MathBlock.Matrix(rows)
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

        /** 输入小数的位数上限。再深下去，精确逆的分母会长到没有阅读价值。 */
        private const val MAX_EXACT_SCALE = 12

        /** 结果分子/分母的位数上限。超出就退回小数显示。 */
        private const val MAX_EXACT_DIGITS = 18
    }
}

/**
 * 约分后的有理数，专供精确矩阵求逆使用。
 *
 * 不变式：`den > 0`、已约分、`den` 永远不为零。
 * 精度没有上限——这正是它能给出 `1/3` 而不是 `0.3333…` 的原因；代价是位数可能膨胀，
 * 所以调用方在出口处按位数决定是否接受。
 */
private data class Frac(val num: BigInteger, val den: BigInteger) {

    val isZero: Boolean get() = num.signum() == 0

    /** 分子分母中较长者的位数，用于出口的膨胀检查。 */
    val digits: Int
        get() = maxOf(num.abs().toString().length, den.toString().length)

    operator fun plus(other: Frac): Frac = of(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: Frac): Frac = of(num * other.den - other.num * den, den * other.den)

    operator fun times(other: Frac): Frac = of(num * other.num, den * other.den)

    operator fun div(other: Frac): Frac = of(num * other.den, den * other.num)

    /** 渲染成真分式；负号提到分式外面，读起来才是 `−1/3` 而不是 `−1` 压在 `3` 上。 */
    fun toBlock(): MathBlock {
        val negative = num.signum() < 0
        val magnitude = if (den == BigInteger.ONE) {
            MathBlock.Number(num.abs().toString())
        } else {
            MathBlock.Fraction(
                MathBlock.Number(num.abs().toString()),
                MathBlock.Number(den.toString())
            )
        }
        return if (negative) MathBlock.Row(listOf(MathBlock.Operator("−"), magnitude)) else magnitude
    }

    companion object {
        val ZERO = Frac(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Frac(BigInteger.ONE, BigInteger.ONE)

        fun of(value: BigDecimal): Frac {
            val stripped = value.stripTrailingZeros()
            return if (stripped.scale() <= 0) {
                of(stripped.toBigIntegerExact(), BigInteger.ONE)
            } else {
                of(stripped.unscaledValue(), BigInteger.TEN.pow(stripped.scale()))
            }
        }

        fun of(numerator: BigInteger, denominator: BigInteger): Frac {
            require(denominator.signum() != 0) { "denominator must not be zero" }
            var num = numerator
            var den = denominator
            if (den.signum() < 0) {
                num = num.negate()
                den = den.negate()
            }
            val gcd = num.gcd(den)
            if (gcd > BigInteger.ONE) {
                num = num.divide(gcd)
                den = den.divide(gcd)
            }
            return Frac(num, den)
        }
    }
}