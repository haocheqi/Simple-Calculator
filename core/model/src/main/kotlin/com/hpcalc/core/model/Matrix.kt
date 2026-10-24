package com.hpcalc.core.model

import java.math.BigDecimal

/**
 * 实数矩阵，行数/列数各自独立取值 `1..6`。
 *
 * 迁移自 v2.0 `CalculatorModels.kt` 里的 `BigDecimalMatrix`。领域层本来就允许非方阵，
 * 是 v2.0 的界面把它锁死成了 `n×n`；这里保留矩形能力，由阶段 3 的界面解锁。
 */
data class Matrix(val rows: List<List<BigDecimal>>) {

    val rowCount: Int get() = rows.size

    val columnCount: Int get() = rows.firstOrNull()?.size ?: 0

    val isSquare: Boolean get() = rowCount == columnCount

    init {
        require(rowCount in 1..MAX_SIZE) { "Row count must be 1..$MAX_SIZE, got $rowCount" }
        require(columnCount in 1..MAX_SIZE) { "Column count must be 1..$MAX_SIZE, got $columnCount" }
        require(rows.all { it.size == columnCount }) { "All rows must have $columnCount entries" }
    }

    fun transpose(): Matrix =
        Matrix((0 until columnCount).map { c -> (0 until rowCount).map { r -> rows[r][c] } })

    fun element(r: Int, c: Int): BigDecimal = rows[r][c]

    companion object {
        /** 单边最大尺寸。与 v2.0 保持一致的容量上限。 */
        const val MAX_SIZE: Int = 6

        fun identity(n: Int): Matrix {
            require(n in 1..MAX_SIZE) { "Size must be 1..$MAX_SIZE, got $n" }
            return Matrix((0 until n).map { r -> (0 until n).map { c -> if (r == c) BigDecimal.ONE else BigDecimal.ZERO } })
        }
    }
}

/** 矩阵运算请求。迁移自 v2.0 `CalculatorModels.kt`。 */
sealed interface MatrixOperation {
    data class Add(val left: Matrix, val right: Matrix) : MatrixOperation
    data class Subtract(val left: Matrix, val right: Matrix) : MatrixOperation
    data class Multiply(val left: Matrix, val right: Matrix) : MatrixOperation
    data class ScalarMultiply(val matrix: Matrix, val scalar: BigDecimal) : MatrixOperation
    data class Transpose(val matrix: Matrix) : MatrixOperation
    data class Determinant(val matrix: Matrix) : MatrixOperation
    data class Inverse(val matrix: Matrix) : MatrixOperation
    data class Rank(val matrix: Matrix) : MatrixOperation
}