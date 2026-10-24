package com.hpcalc.core.math

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 结果格式化规则的回归测试。 */
class FormattingTest {

    @Test
    fun `ordinary magnitudes stay in plain notation`() {
        assertEquals("1234.5678", formatResult(BigDecimal("1234.5678")))
    }

    @Test
    fun `trailing zeros are stripped`() {
        assertEquals("2.5", formatResult(BigDecimal("2.500")))
    }

    @Test
    fun `large magnitudes switch to engineering notation`() {
        assertTrue(formatResult(BigDecimal("1e20")).contains("E"))
    }

    @Test
    fun `small magnitudes switch to engineering notation`() {
        assertTrue(formatResult(BigDecimal("1e-8")).contains("E"))
    }

    @Test
    fun `zero stays plain`() {
        assertEquals("0", formatResult(BigDecimal.ZERO))
    }

    @Test
    fun `significant digits are honoured`() {
        assertEquals("1.234567890123457", formatResult(BigDecimal("1.23456789012345678")))
    }
}