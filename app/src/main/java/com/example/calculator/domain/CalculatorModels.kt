package com.example.calculator.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalculatorMode { BASIC, SCIENTIFIC, SYMBOLIC, MATRIX }
enum class AngleUnit { RADIANS, DEGREES, GRADIANS }

data class EvaluationContext(
    val mathContext: MathContext = MathContext(50, RoundingMode.HALF_UP),
    val angleUnit: AngleUnit = AngleUnit.RADIANS,
    val ans: BigDecimal? = null
)

sealed interface EvaluationResult {
    data class Success(val value: BigDecimal) : EvaluationResult
    data class Error(val message: String) : EvaluationResult
}

sealed interface SymbolicResult {
    data class Success(val expression: String) : SymbolicResult
    data class Error(val message: String) : SymbolicResult
}

sealed interface MatrixResult {
    data class Success(val matrix: BigDecimalMatrix) : MatrixResult
    data class Scalar(val value: BigDecimal) : MatrixResult
    data class Text(val value: String) : MatrixResult
    data class Error(val message: String) : MatrixResult
}

data class BigDecimalMatrix(val rows: List<List<BigDecimal>>) {
    val rowCount get() = rows.size
    val columnCount get() = rows.firstOrNull()?.size ?: 0
    init { require(rowCount in 1..6 && columnCount in 1..6 && rows.all { it.size == columnCount }) }
    fun transpose() = BigDecimalMatrix((0 until columnCount).map { c -> (0 until rowCount).map { r -> rows[r][c] } })
}

sealed interface MatrixOperation {
    data class Add(val left: BigDecimalMatrix, val right: BigDecimalMatrix): MatrixOperation
    data class Subtract(val left: BigDecimalMatrix, val right: BigDecimalMatrix): MatrixOperation
    data class Multiply(val left: BigDecimalMatrix, val right: BigDecimalMatrix): MatrixOperation
    data class ScalarMultiply(val matrix: BigDecimalMatrix, val scalar: BigDecimal): MatrixOperation
    data class Transpose(val matrix: BigDecimalMatrix): MatrixOperation
    data class Determinant(val matrix: BigDecimalMatrix): MatrixOperation
    data class Inverse(val matrix: BigDecimalMatrix): MatrixOperation
    data class Rank(val matrix: BigDecimalMatrix): MatrixOperation
}

interface NumericEvaluator { fun evaluate(expression: String, context: EvaluationContext = EvaluationContext()): EvaluationResult }
interface SymbolicEvaluator {
    fun simplify(expression: String): SymbolicResult
    fun substitute(expression: String, values: Map<String, BigDecimal>): SymbolicResult
}
interface MatrixEvaluator { fun evaluate(operation: MatrixOperation): MatrixResult }
