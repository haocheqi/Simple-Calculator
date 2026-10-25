package com.hpcalc.core.mathui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 缩放系数的纯数学部分。
 *
 * 排版本身要有 Compose 运行时才能测，但「该缩多少」是纯算术，
 * 而它正是矩阵真分式撑爆显示区那个 bug 的关键。
 */
class ScaleToFitTest {

    private val tolerance = 0.0001f

    private fun assertScale(expected: Float, actual: Float) {
        assertEquals(expected, actual, tolerance)
    }

    @Test
    fun `content that already fits is not scaled`() {
        assertScale(1f, fitScale(naturalWidth = 100f, naturalHeight = 50f, maxWidth = 200f, maxHeight = 200f))
    }

    @Test
    fun `width is the binding constraint`() {
        assertScale(0.5f, fitScale(naturalWidth = 400f, naturalHeight = 100f, maxWidth = 200f, maxHeight = 400f))
    }

    @Test
    fun `height is the binding constraint`() {
        assertScale(0.25f, fitScale(naturalWidth = 100f, naturalHeight = 400f, maxWidth = 400f, maxHeight = 100f))
    }

    @Test
    fun `the smaller of the two ratios wins`() {
        // 2×2 的真分式矩阵就是这样撞上高度的。
        assertScale(0.4f, fitScale(naturalWidth = 500f, naturalHeight = 250f, maxWidth = 400f, maxHeight = 100f))
    }

    @Test
    fun `an unbounded axis is ignored`() {
        assertScale(
            0.5f,
            fitScale(
                naturalWidth = 400f,
                naturalHeight = 400f,
                maxWidth = Float.POSITIVE_INFINITY,
                maxHeight = 200f
            )
        )
    }

    @Test
    fun `zero sized content is left alone`() {
        assertScale(1f, fitScale(naturalWidth = 0f, naturalHeight = 0f, maxWidth = 100f, maxHeight = 100f))
    }

    @Test
    fun `an unbounded box with infinite content does not divide by zero`() {
        assertScale(
            1f,
            fitScale(
                naturalWidth = Float.POSITIVE_INFINITY,
                naturalHeight = 10f,
                maxWidth = 100f,
                maxHeight = 100f
            )
        )
    }
}