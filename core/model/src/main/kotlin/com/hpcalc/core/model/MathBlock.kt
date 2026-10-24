package com.hpcalc.core.model

/**
 * 可渲染结果树，标量、符号、矩阵共用的唯一呈现契约。
 *
 * **阶段 1 保持 v2.0 的形状不变**，目的是让旧界面零回归地接上新模块。
 * 阶段 2 会连同新界面一起重塑它：`Scalar` 拆为 `Number`/`Text`，
 * 新增 `Row`/`Operator`/`Symbol`/`Radical`，`Matrix` 增加左右定界符。
 * 当前形态下 `Fraction`/`Superscript`/`Function`/`Sequence` 没有任何生产者，
 * 属于 v2.0 遗留的空壳节点。
 */
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

fun Matrix.toMathBlock(): MathBlock.Matrix =
    MathBlock.Matrix(rows.map { row -> row.map { MathBlock.Scalar(it.stripTrailingZeros().toPlainString()) } })