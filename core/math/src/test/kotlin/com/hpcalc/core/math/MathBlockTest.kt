package com.hpcalc.core.math

import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.asMathRow
import com.hpcalc.core.model.plainText
import org.junit.Assert.assertEquals
import org.junit.Test

/** 呈现树自身的规则：扁平化文本与序列折叠。 */
class MathBlockTest {

    @Test
    fun `fraction flattens with parentheses`() {
        val block = MathBlock.Fraction(MathBlock.Number("1"), MathBlock.Number("3"))
        assertEquals("(1)/(3)", block.plainText())
    }

    @Test
    fun `power flattens with a caret`() {
        val block = MathBlock.Power(MathBlock.Number("2"), MathBlock.Number("3"))
        assertEquals("2^3", block.plainText())
    }

    @Test
    fun `matrix flattens row by row`() {
        val block = MathBlock.Matrix(
            listOf(
                listOf(MathBlock.Number("1"), MathBlock.Number("2")),
                listOf(MathBlock.Number("3"), MathBlock.Number("4"))
            )
        )
        assertEquals("1, 2; 3, 4", block.plainText())
    }

    @Test
    fun `error flattens to its message`() {
        assertEquals(MathError.DivisionByZero.message, MathBlock.Error(MathError.DivisionByZero).plainText())
    }

    @Test
    fun `row joins children with single spaces`() {
        val row = MathBlock.Row(listOf(MathBlock.Number("1"), MathBlock.Operator("+"), MathBlock.Number("2")))
        assertEquals("1 + 2", row.plainText())
    }

    @Test
    fun `single element row collapses to its child`() {
        assertEquals(MathBlock.Number("1"), listOf(MathBlock.Number("1")).asMathRow())
    }

    @Test
    fun `empty row becomes empty text`() {
        assertEquals(MathBlock.Text(""), emptyList<MathBlock>().asMathRow())
    }

    @Test
    fun `multi element row keeps the wrapper`() {
        val row = listOf(MathBlock.Number("1"), MathBlock.Number("2")).asMathRow()
        assertEquals(2, (row as MathBlock.Row).children.size)
    }

    @Test
    fun `radical flattens with a root sign`() {
        assertEquals("√(2)", MathBlock.Radical(MathBlock.Number("2")).plainText())
    }
}