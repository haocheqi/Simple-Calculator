package com.example.calculator.domain

/** Renderable result tree shared by scalar, symbolic and matrix output. */
sealed interface MathBlock {
    data class Scalar(val value: String) : MathBlock
    data class Text(val value: String) : MathBlock
    data class Fraction(val numerator: MathBlock, val denominator: MathBlock) : MathBlock
    data class Superscript(val base: MathBlock, val exponent: MathBlock) : MathBlock
    data class Function(val name: String, val argument: MathBlock) : MathBlock
    data class Matrix(val rows: List<List<MathBlock>>) : MathBlock
    data class Sequence(val children: List<MathBlock>) : MathBlock
    data class Error(val message: String) : MathBlock
}

fun BigDecimalMatrix.toMathBlock(): MathBlock.Matrix = MathBlock.Matrix(rows.map { row -> row.map { MathBlock.Scalar(it.stripTrailingZeros().toPlainString()) } })
