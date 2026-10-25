package com.hpcalc.core.mathui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hpcalc.core.model.MathBlock
import kotlin.math.max

/** 数学排版样式。颜色必须由调用方从主题里取；未指定时退化为黑白。 */
data class MathTextStyle(
    val fontSize: TextUnit = 26.sp,
    val color: Color = Color.Unspecified,
    val errorColor: Color = Color.Unspecified,
    /** 分数线、定界符、根号等结构线条的颜色。 */
    val accentColor: Color = Color.Unspecified
)

/**
 * 一次测量得到的排版结果。
 *
 * 数学排版的关键是**基线**：`ascent` 是盒顶到基线的距离，`descent` 是基线到盒底。
 * 横向排列时所有子盒共用同一条基线，而不是垂直居中，否则分式和指数会错位。
 */
class MeasuredBlock(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    private val render: DrawScope.(x: Float, baselineY: Float) -> Unit
) {
    val height: Float get() = ascent + descent

    fun DrawScope.drawAt(x: Float, baselineY: Float) = render(x, baselineY)
}

fun measureMathBlock(
    block: MathBlock,
    measurer: TextMeasurer,
    style: MathTextStyle,
    density: Density
): MeasuredBlock {
    val fontSizePx = with(density) { style.fontSize.toPx() }
    return Typesetter(measurer, style, fontSizePx).measure(block)
}

private fun Color.orElse(fallback: Color): Color = if (isSpecified) this else fallback

private class Typesetter(
    private val measurer: TextMeasurer,
    private val style: MathTextStyle,
    private val fontSizePx: Float
) {
    private val textColor = style.color.orElse(Color(0xFF1B1B1F))
    private val structureColor = style.accentColor.orElse(textColor)
    private val errorColor = style.errorColor.orElse(Color(0xFFB3261E))

    private val baseStyle = TextStyle(fontSize = style.fontSize, color = textColor)
    private val errorStyle = TextStyle(fontSize = style.fontSize, color = errorColor)

    private val hairline: Float get() = max(1f, fontSizePx * 0.045f)
    private val scriptScale = 0.68f

    fun measure(block: MathBlock): MeasuredBlock = when (block) {
        is MathBlock.Number -> text(block.literal, baseStyle)
        is MathBlock.Symbol -> text(block.name, baseStyle)
        is MathBlock.Operator -> text(block.text, baseStyle)
        is MathBlock.Text -> text(block.value, baseStyle)
        is MathBlock.Error -> text(block.error.message, errorStyle)
        is MathBlock.Row -> row(block.children)
        is MathBlock.Fraction -> fraction(block.numerator, block.denominator)
        is MathBlock.Power -> power(block.base, block.exponent)
        is MathBlock.Radical -> radical(block.radicand, block.index)
        is MathBlock.Matrix -> matrix(block)
    }

    private fun scaled(scale: Float): Typesetter = Typesetter(
        measurer = measurer,
        style = style.copy(fontSize = style.fontSize * scale),
        fontSizePx = fontSizePx * scale
    )

    private fun empty(): MeasuredBlock = MeasuredBlock(0f, 0f, 0f) { _, _ -> }

    private fun text(value: String, textStyle: TextStyle): MeasuredBlock {
        if (value.isEmpty()) return empty()
        val layout = measurer.measure(AnnotatedString(value), textStyle)
        val baseline = layout.firstBaseline.takeIf { it > 0f } ?: (layout.size.height * 0.8f)
        val descent = (layout.size.height - baseline).coerceAtLeast(0f)
        return MeasuredBlock(
            width = layout.size.width.toFloat(),
            ascent = baseline,
            descent = descent
        ) { x, baselineY -> drawText(layout, topLeft = Offset(x, baselineY - baseline)) }
    }

    private fun row(children: List<MathBlock>): MeasuredBlock {
        val measured = children.map { measure(it) }
        if (measured.isEmpty()) return empty()
        val gap = fontSizePx * 0.06f
        val ascent = measured.maxOf { it.ascent }
        val descent = measured.maxOf { it.descent }
        var width = 0f
        measured.forEachIndexed { index, child ->
            width += child.width
            if (index > 0) width += gap
        }
        return MeasuredBlock(width, ascent, descent) { x, baselineY ->
            var cursor = x
            measured.forEach { child ->
                with(child) { drawAt(cursor, baselineY) }
                cursor += child.width + gap
            }
        }
    }

    private fun fraction(numerator: MathBlock, denominator: MathBlock): MeasuredBlock {
        val top = measure(numerator)
        val bottom = measure(denominator)
        val gap = fontSizePx * 0.18f
        val thickness = hairline
        val sidePad = fontSizePx * 0.16f
        val width = max(top.width, bottom.width) + sidePad * 2f
        val ascent = top.height + gap + thickness / 2f
        val descent = gap + thickness / 2f + bottom.height
        return MeasuredBlock(width, ascent, descent) { x, baselineY ->
            drawLine(
                color = structureColor,
                start = Offset(x + sidePad * 0.5f, baselineY),
                end = Offset(x + width - sidePad * 0.5f, baselineY),
                strokeWidth = thickness
            )
            val numeratorX = x + (width - top.width) / 2f
            with(top) { drawAt(numeratorX, baselineY - gap - thickness / 2f - top.descent) }
            val denominatorX = x + (width - bottom.width) / 2f
            with(bottom) { drawAt(denominatorX, baselineY + gap + thickness / 2f + bottom.ascent) }
        }
    }

    private fun power(base: MathBlock, exponent: MathBlock): MeasuredBlock {
        val baseBlock = measure(base)
        val exponentBlock = scaled(scriptScale).measure(exponent)
        val raise = fontSizePx * 0.42f
        val gap = fontSizePx * 0.04f
        val width = baseBlock.width + gap + exponentBlock.width
        val ascent = max(baseBlock.ascent, exponentBlock.ascent + raise)
        val descent = max(baseBlock.descent, exponentBlock.descent - raise).coerceAtLeast(0f)
        return MeasuredBlock(width, ascent, descent) { x, baselineY ->
            with(baseBlock) { drawAt(x, baselineY) }
            with(exponentBlock) { drawAt(x + baseBlock.width + gap, baselineY - raise) }
        }
    }

    private fun radical(radicand: MathBlock, index: MathBlock?): MeasuredBlock {
        val body = measure(radicand)
        val indexBlock = index?.let { scaled(0.55f).measure(it) }
        val gap = fontSizePx * 0.14f
        val glyphWidth = fontSizePx * 0.5f
        val overhang = fontSizePx * 0.08f
        val indexWidth = indexBlock?.width ?: 0f
        val width = indexWidth + glyphWidth + body.width + overhang
        val ascent = body.ascent + gap + (indexBlock?.height ?: 0f) * 0.35f
        val descent = body.descent + fontSizePx * 0.08f
        return MeasuredBlock(width, ascent, descent) { x, baselineY ->
            val overlineY = baselineY - body.ascent - gap
            val bottomY = baselineY + body.descent * 0.5f
            val glyphLeft = x + indexWidth
            val glyphRight = glyphLeft + glyphWidth

            drawLine(
                color = structureColor,
                start = Offset(glyphRight - glyphWidth * 0.1f, overlineY),
                end = Offset(x + width, overlineY),
                strokeWidth = hairline
            )

            val path = Path().apply {
                moveTo(glyphLeft, bottomY - (bottomY - overlineY) * 0.42f)
                lineTo(glyphLeft + glyphWidth * 0.3f, bottomY)
                lineTo(glyphLeft + glyphWidth * 0.62f, overlineY)
                lineTo(glyphRight, overlineY)
            }
            drawPath(path, structureColor, style = Stroke(width = hairline, cap = StrokeCap.Round))

            with(body) { drawAt(glyphRight, baselineY) }
            indexBlock?.let { block ->
                with(block) { drawAt(x, overlineY - block.descent) }
            }
        }
    }

    private fun matrix(block: MathBlock.Matrix): MeasuredBlock {
        val cells = block.rows.map { row -> row.map { measure(it) } }
        if (cells.isEmpty()) return empty()
        val columnCount = cells.maxOf { it.size }
        if (columnCount == 0) return empty()

        val columnWidths = FloatArray(columnCount) { column ->
            cells.maxOfOrNull { row -> row.getOrNull(column)?.width ?: 0f } ?: 0f
        }
        val rowHeights = FloatArray(cells.size) { row -> cells[row].maxOfOrNull { it.height } ?: 0f }

        val columnGap = fontSizePx * 0.6f
        val rowGap = fontSizePx * 0.22f
        val contentWidth = columnWidths.sum() + columnGap * (columnCount - 1)
        val contentHeight = rowHeights.sum() + rowGap * (cells.size - 1)
        val padding = fontSizePx * 0.22f
        val delimiterWidth = fontSizePx * 0.3f
        val width = delimiterWidth * 2f + contentWidth + padding * 2f
        val halfHeight = contentHeight / 2f + padding
        val ascent = halfHeight
        val descent = halfHeight

        return MeasuredBlock(width, ascent, descent) { x, baselineY ->
            val top = baselineY - ascent
            val contentLeft = x + delimiterWidth + padding

            var y = top + padding
            cells.forEachIndexed { rowIndex, rowCells ->
                var cursorX = contentLeft
                rowCells.forEachIndexed { columnIndex, cell ->
                    val cellWidth = columnWidths[columnIndex]
                    val cellX = cursorX + (cellWidth - cell.width) / 2f
                    val cellBaseline = y + (rowHeights[rowIndex] - cell.height) / 2f + cell.ascent
                    with(cell) { drawAt(cellX, cellBaseline) }
                    cursorX += cellWidth + columnGap
                }
                y += rowHeights[rowIndex] + rowGap
            }

            val delimiterHeight = contentHeight + padding
            drawDelimiter(
                kind = block.delimiter,
                left = x,
                width = delimiterWidth,
                top = top + padding * 0.5f,
                height = delimiterHeight,
                mirrored = false
            )
            drawDelimiter(
                kind = block.delimiter,
                left = x + width - delimiterWidth,
                width = delimiterWidth,
                top = top + padding * 0.5f,
                height = delimiterHeight,
                mirrored = true
            )
        }
    }

    private fun DrawScope.drawDelimiter(
        kind: MathBlock.Delimiter,
        left: Float,
        width: Float,
        top: Float,
        height: Float,
        mirrored: Boolean
    ) {
        val bottom = top + height
        when (kind) {
            MathBlock.Delimiter.BAR -> {
                val x = left + width * 0.5f
                drawLine(structureColor, Offset(x, top), Offset(x, bottom), hairline)
            }
            MathBlock.Delimiter.DOUBLE_BAR -> {
                val first = left + width * 0.3f
                val second = left + width * 0.7f
                drawLine(structureColor, Offset(first, top), Offset(first, bottom), hairline)
                drawLine(structureColor, Offset(second, top), Offset(second, bottom), hairline)
            }
            MathBlock.Delimiter.BRACKET -> {
                val spineX = if (mirrored) left + width else left
                val armX = if (mirrored) left else left + width
                drawLine(structureColor, Offset(spineX, top), Offset(armX, top), hairline)
                drawLine(structureColor, Offset(spineX, top), Offset(spineX, bottom), hairline)
                drawLine(structureColor, Offset(spineX, bottom), Offset(armX, bottom), hairline)
            }
            MathBlock.Delimiter.PAREN -> {
                val path = Path()
                if (!mirrored) {
                    path.moveTo(left + width, top)
                    path.cubicTo(
                        left + width * 0.15f, top + height * 0.22f,
                        left + width * 0.15f, bottom - height * 0.22f,
                        left + width, bottom
                    )
                } else {
                    path.moveTo(left, top)
                    path.cubicTo(
                        left + width * 0.85f, top + height * 0.22f,
                        left + width * 0.85f, bottom - height * 0.22f,
                        left, bottom
                    )
                }
                drawPath(path, structureColor, style = Stroke(width = hairline, cap = StrokeCap.Round))
            }
        }
    }
}