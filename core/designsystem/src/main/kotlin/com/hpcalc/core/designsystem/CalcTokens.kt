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

    val FunctionStripHeight = 40.dp

    /**
     * 函数条每行放几个键。
     *
     * 科学模式有 17 个函数，定成 6 个一行正好 3 行（6/6/5），
     * 按下标补齐最后一行的空位，所有键等宽，不依赖屏幕宽度。
     */
    const val FunctionColumns = 6
}