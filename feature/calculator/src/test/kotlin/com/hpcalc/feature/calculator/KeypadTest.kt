package com.hpcalc.feature.calculator

import com.hpcalc.core.designsystem.CalcTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键盘布局的回归测试。
 *
 * 退格键曾经「定义好了但没人把它放进 `mainKeypad`」，编译器不会吭声，测试也不覆盖，
 * 结果就是真机上根本没有 DEL。这里把布局当成数据来断言：每个必需的动作都必须可达。
 */
class KeypadTest {

    private val rows = mainKeypad
    private val keys = rows.flatten()

    @Test
    fun `the keypad is a full four by five grid`() {
        assertEquals(5, rows.size)
        rows.forEachIndexed { index, row ->
            assertEquals("row $index should have 4 keys", 4, row.size)
        }
        assertEquals(20, keys.size)
    }

    @Test
    fun `every action key is reachable`() {
        assertEquals(1, keys.count { it.action == KeyAction.CLEAR })
        assertEquals("DEL must be on the keypad", 1, keys.count { it.action == KeyAction.DELETE })
        assertEquals(1, keys.count { it.action == KeyAction.EVALUATE })
    }

    @Test
    fun `all ten digits appear exactly once`() {
        (0..9).forEach { digit ->
            assertEquals("digit $digit", 1, keys.count { it.insert == digit.toString() })
        }
    }

    @Test
    fun `the four operators and the decimal point are present`() {
        listOf("+", "-", "*", "/", ".", "(", ")").forEach { token ->
            assertEquals("token $token", 1, keys.count { it.insert == token })
        }
    }

    @Test
    fun `equals sits at the end of the operator column`() {
        val last = rows.last()
        assertEquals(KeyAction.EVALUATE, last[2].action)
        assertEquals("+", last[3].insert)
    }

    @Test
    fun `the function strips have unique, well formed labels`() {
        assertTrue(scientificFunctions.isNotEmpty())
        assertTrue(symbolicFunctions.isNotEmpty())
        listOf(scientificFunctions, symbolicFunctions).forEach { strip ->
            val labels = strip.map { it.label }
            assertEquals("duplicate label in $labels", labels.size, labels.toSet().size)
            labels.forEach { assertTrue("blank label", it.isNotBlank()) }
            strip.forEach { assertTrue("no insert token for ${it.label}", !it.insert.isNullOrEmpty()) }
        }
    }

    @Test
    fun `every insert token survives a round trip through the evaluator alphabet`() {
        // 键盘只能输入求值器认得的字符，否则用户点出来的算式根本算不了。
        val allowed = "0123456789.+-*/()^%! ".toSet()
        keys.forEach { key ->
            val insert = key.insert
            if (!insert.isNullOrEmpty()) {
                assertTrue(
                    "unexpected token '$insert' on key ${key.label}",
                    insert.all { it in allowed || it.isLetter() }
                )
            }
        }
    }
    @Test
    fun `the function strips fit in three rows`() {
        // 界面按 CalcTokens.FunctionColumns 折行。多到第四行就会把显示区挤没，
        // 而「3 行、不用横向滑动」正是验收项 K07 的一半。
        listOf(scientificFunctions, symbolicFunctions).forEach { strip ->
            val rows = (strip.size + CalcTokens.FunctionColumns - 1) / CalcTokens.FunctionColumns
            assertTrue("${strip.size} keys need $rows rows", rows <= 3)
        }
    }
}
