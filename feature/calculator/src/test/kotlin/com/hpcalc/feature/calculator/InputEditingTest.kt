package com.hpcalc.feature.calculator

import com.hpcalc.core.model.MathBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 输入编辑与结果形态。
 *
 * 光标能在算式中间，是这批改动里最容易写错的地方：插入点、退格边界、上限截断，
 * 一旦错位就会出现「点哪里都插在末尾」或者「退格删错字符」。
 */
class InputEditingTest {

    private val limit = 32

    @Test
    fun `inserting at the end appends`() {
        val result = insertAt("1+2", 3, "*3", limit)
        assertEquals("1+2*3", result.expression)
        assertEquals(5, result.caret)
    }

    @Test
    fun `inserting in the middle splices`() {
        val result = insertAt("1+2", 2, "9", limit)
        assertEquals("1+92", result.expression)
        assertEquals(3, result.caret)
    }

    @Test
    fun `inserting at the start prepends`() {
        val result = insertAt("2", 0, "-", limit)
        assertEquals("-2", result.expression)
        assertEquals(1, result.caret)
    }

    @Test
    fun `inserting past the end is clamped`() {
        val result = insertAt("12", 99, "3", limit)
        assertEquals("123", result.expression)
        assertEquals(3, result.caret)
    }

    @Test
    fun `the length limit truncates the inserted text`() {
        val full = "x".repeat(limit)
        val result = insertAt(full, limit, "abc", limit)
        assertEquals(limit, result.expression.length)
        assertEquals(limit, result.caret)
    }

    @Test
    fun `a partially full expression only accepts what fits`() {
        val result = insertAt("x".repeat(limit - 1), limit - 1, "abc", limit)
        assertEquals(limit, result.expression.length)
        assertTrue(result.expression.endsWith("a"))
    }

    @Test
    fun `backspace removes the character before the caret`() {
        val result = deleteBefore("1+23", 3)
        assertEquals("1+3", result.expression)
        assertEquals(2, result.caret)
    }

    @Test
    fun `backspace at the start does nothing`() {
        val result = deleteBefore("123", 0)
        assertEquals("123", result.expression)
        assertEquals(0, result.caret)
    }

    @Test
    fun `backspace past the end deletes the last character`() {
        val result = deleteBefore("123", 99)
        assertEquals("12", result.expression)
        assertEquals(2, result.caret)
    }

    @Test
    fun `caret is clamped into the expression`() {
        assertEquals(0, moveCaret("123", -5))
        assertEquals(3, moveCaret("123", 42))
        assertEquals(2, moveCaret("123", 2))
    }

    @Test
    fun `the shown result follows the S-D toggle`() {
        val decimal = MathBlock.Number("0.3333333333333333")
        val fraction = MathBlock.Fraction(MathBlock.Number("1"), MathBlock.Number("3"))
        val state = CalculatorUiState(
            expression = "1/3",
            result = decimal,
            exactResult = fraction
        )

        assertFalse(state.showsExactFraction)
        assertEquals(decimal, state.shownResult)
        assertTrue(state.canToggleForm)

        val toggled = state.copy(showsExactFraction = true)
        assertEquals(fraction, toggled.shownResult)
    }

    @Test
    fun `results without an exact form cannot toggle`() {
        val state = CalculatorUiState(result = MathBlock.Number("0.8414709848"))
        assertFalse(state.canToggleForm)
        // 即使把标志位强行打开，也只显示小数——不会画一个不存在的分数。
        assertEquals(state.result, state.copy(showsExactFraction = true).shownResult)
    }

    @Test
    fun `caret at end is reported for the implicit caret`() {
        assertTrue(CalculatorUiState(expression = "1+2", caret = 3).caretAtEnd)
        assertFalse(CalculatorUiState(expression = "1+2", caret = 1).caretAtEnd)
    }

    @Test
    fun `an integral result has no second form to toggle`() {
        // 5 的分数形态还是 5，给出开关只会让人以为按钮坏了。
        val state = CalculatorUiState(
            expression = "2+3",
            result = MathBlock.Number("5"),
            exactResult = MathBlock.Number("5")
        )
        assertFalse(state.canToggleForm)
    }

    @Test
    fun `a genuine fraction enables the toggle`() {
        val state = CalculatorUiState(
            result = MathBlock.Number("0.5"),
            exactResult = MathBlock.Fraction(MathBlock.Number("1"), MathBlock.Number("2"))
        )
        assertTrue(state.canToggleForm)
    }

    @Test
    fun `settings are not duplicated inside the ui state`() {
        // 设置只有一个事实来源：DataStore -> state.settings。
        // 状态里再存一份 displayMode 副本，求值时读到旧值，会让「精确分数」设置彻底失效——
        // 这个 bug 真的发生过，所以用测试钉住结构。
        val fields = CalculatorUiState::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("displayMode 应从 state.settings 读", "displayMode" in fields)
        assertFalse("angleUnit 应从 state.settings 读", "angleUnit" in fields)
        assertTrue("settings 必须在状态里", "settings" in fields)
    }
}
