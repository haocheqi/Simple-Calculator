package com.hpcalc.core.designsystem

import androidx.compose.ui.unit.dp

/** 计算器界面的尺寸 token。所有间距、圆角只从这里取，避免散落魔法数。 */
object CalcTokens {
    val KeyHeight = 58.dp
    val KeyGap = 8.dp
    val KeyCorner = 18.dp
    val ScreenPadding = 16.dp
    val SectionSpacing = 12.dp
    val DisplayMinHeight = 132.dp

    /**
     * 函数键的高度与宽度。
     *
     * 科学模式要放 17 个函数，48dp 宽刚好能在 360dp 屏上每行放 6 个、
     * 折成 3 行而不是要求横向滚动。
     */
    val FunctionStripHeight = 40.dp
    val FunctionKeyWidth = 48.dp
}