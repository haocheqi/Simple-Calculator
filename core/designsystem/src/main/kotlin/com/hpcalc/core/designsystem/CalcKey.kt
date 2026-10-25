package com.hpcalc.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 按键的语义分类，决定配色与字号。 */
enum class CalcKeyStyle {
    /** 数字与小数点，视觉上最轻。 */
    NUMBER,

    /** 四则运算符。 */
    OPERATOR,

    /** 科学函数与常量，字号更小以容纳更长的标签。 */
    FUNCTION,

    /** 等号，全场唯一的强调块。 */
    EQUAL,

    /** 清除、退格一类破坏性操作。 */
    DANGER
}

/**
 * 计算器按键。
 *
 * 设计取向：默认界面尽量安静——数字键几乎不设底色，只有等号用主题色强调；
 * 科学模式的函数键更小、更淡，靠密度而不是颜色区分层级。
 */
@Composable
fun CalcKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CalcKeyStyle = CalcKeyStyle.NUMBER,
    description: String? = null,
    minHeight: Dp = CalcTokens.KeyHeight
) {
    val scheme = MaterialTheme.colorScheme
    val container: Color = when (style) {
        CalcKeyStyle.NUMBER -> scheme.surface
        CalcKeyStyle.OPERATOR -> scheme.surfaceVariant
        CalcKeyStyle.FUNCTION -> scheme.surfaceVariant.copy(alpha = 0.45f)
        CalcKeyStyle.EQUAL -> scheme.primary
        CalcKeyStyle.DANGER -> scheme.secondaryContainer
    }
    val content: Color = when (style) {
        CalcKeyStyle.NUMBER -> scheme.onSurface
        CalcKeyStyle.OPERATOR -> scheme.onSurface
        CalcKeyStyle.FUNCTION -> scheme.onSurfaceVariant
        CalcKeyStyle.EQUAL -> scheme.onPrimary
        CalcKeyStyle.DANGER -> scheme.onSecondaryContainer
    }
    val fontSize = when (style) {
        CalcKeyStyle.FUNCTION -> 14.sp
        CalcKeyStyle.NUMBER, CalcKeyStyle.OPERATOR -> 24.sp
        CalcKeyStyle.EQUAL -> 26.sp
        CalcKeyStyle.DANGER -> 18.sp
    }
    val weight = when (style) {
        CalcKeyStyle.NUMBER -> FontWeight.Medium
        CalcKeyStyle.EQUAL -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(CalcTokens.KeyCorner),
        color = container
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = content,
                fontSize = fontSize,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}