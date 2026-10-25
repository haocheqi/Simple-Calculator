package com.hpcalc.core.model

/**
 * 可渲染的数学表达式树，标量、符号、矩阵共用的唯一呈现契约。
 *
 * 阶段 2 相对 v2.0 的重塑：
 * - `Scalar` 拆为 [Number]（数值字面量）与 [Text]（原样文本），渲染策略不再混在一个节点里；
 * - 新增 [Row]/[Operator]/[Symbol] 表达算式结构；
 * - `Superscript` 改为语义化的 [Power]（底数 + 指数），上移量交给渲染层决定；
 * - [Fraction] 保留，语义明确为真正的上下分式；
 * - 新增 [Radical]；
 * - [Matrix] 增加左右定界符，不再只能画方括号；
 * - `Error` 携带结构化 [MathError]，不再携带字符串。
 *
 * 渲染契约：任何畸形节点都降级显示，绝不抛异常。
 */
sealed interface MathBlock {

    /** 已格式化的数值字面量，如 `3.14`、`1.2E-8`。 */
    data class Number(val literal: String) : MathBlock

    /** 变量或常量记号，如 `x`、`π`、`ans`。 */
    data class Symbol(val name: String) : MathBlock

    /** 运算符，如 `+`、`−`、`×`、`÷`、`=`。 */
    data class Operator(val text: String) : MathBlock

    /** 横向序列，子节点按基线对齐。 */
    data class Row(val children: List<MathBlock>) : MathBlock

    /** 分数，基线落在分数线上。 */
    data class Fraction(val numerator: MathBlock, val denominator: MathBlock) : MathBlock

    /** 幂，指数相对底数上移。 */
    data class Power(val base: MathBlock, val exponent: MathBlock) : MathBlock

    /** 根式。`index` 为空表示平方根。 */
    data class Radical(val radicand: MathBlock, val index: MathBlock? = null) : MathBlock

    /** 矩阵，带左右定界符。 */
    data class Matrix(
        val rows: List<List<MathBlock>>,
        val delimiter: Delimiter = Delimiter.PAREN
    ) : MathBlock

    /** 原样文本，用于没有结构化 AST 的结果（如化简后的多项式）。 */
    data class Text(val value: String) : MathBlock

    /** 结构化错误。 */
    data class Error(val error: MathError) : MathBlock

    enum class Delimiter { PAREN, BRACKET, BAR, DOUBLE_BAR }
}

/** 把矩阵转成可渲染节点。 */
fun Matrix.toMathBlock(delimiter: MathBlock.Delimiter = MathBlock.Delimiter.PAREN): MathBlock.Matrix =
    MathBlock.Matrix(
        rows = rows.map { row -> row.map { cell -> MathBlock.Number(cell.stripTrailingZeros().toPlainString()) } },
        delimiter = delimiter
    )

/** 单元素序列不再额外套一层 [MathBlock.Row]。 */
fun List<MathBlock>.asMathRow(): MathBlock = when (size) {
    0 -> MathBlock.Text("")
    1 -> first()
    else -> MathBlock.Row(this)
}

/** 把呈现树压平成纯文本，用于历史记录、剪贴板与日志。 */
fun MathBlock.plainText(): String = when (this) {
    is MathBlock.Number -> literal
    is MathBlock.Symbol -> name
    is MathBlock.Operator -> text
    is MathBlock.Text -> value
    is MathBlock.Error -> error.message
    is MathBlock.Row -> children.joinToString(" ") { it.plainText() }
    is MathBlock.Fraction -> "(${numerator.plainText()})/(${denominator.plainText()})"
    is MathBlock.Power -> "${base.plainText()}^${exponent.plainText()}"
    is MathBlock.Radical -> "√(${radicand.plainText()})"
    is MathBlock.Matrix -> rows.joinToString("; ") { row -> row.joinToString(", ") { it.plainText() } }
}