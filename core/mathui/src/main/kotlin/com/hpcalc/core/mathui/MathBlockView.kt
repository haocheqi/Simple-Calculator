package com.hpcalc.core.mathui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError

/** 允许在子树里统一覆盖数学排版样式。 */
val LocalMathTextStyle = compositionLocalOf { MathTextStyle() }

/**
 * 错误文案的解析器。
 *
 * 排版层不认识资源文件，所以默认用 [MathError.message] 这个英文兜底；
 * 界面层应当用 `CompositionLocalProvider` 提供按 `strings.xml` 翻译过的版本。
 * `static` 变体是刻意的：语言切换会重建 Activity，此时整棵子树都该重排。
 */
val LocalMathErrorText = staticCompositionLocalOf<(MathError) -> String> { { it.message } }

/** 从 Material 主题取一套数学排版样式。 */
@Composable
fun rememberMathTextStyle(fontSize: TextUnit = 26.sp): MathTextStyle {
    val colors = MaterialTheme.colorScheme
    return remember(fontSize, colors) {
        MathTextStyle(
            fontSize = fontSize,
            color = colors.onSurface,
            errorColor = colors.error,
            accentColor = colors.onSurfaceVariant
        )
    }
}

/**
 * 用 Compose 自绘的方式渲染数学表达式。
 *
 * 不使用 WebView/KaTeX：整棵 [MathBlock] 树在这里被测量成「宽 + 基线上下高度」，
 * 再一次性画到 [Canvas] 上。好处是跟随主题、跟随字体缩放、随 Compose 一起重组，
 * 代价是需要自己实现基线对齐、分式、上下标、根号与矩阵定界符。
 *
 * **超界时等比缩小。** 矩阵里塞进真分式后，一格就有两行高还带分数线，
 * 2×2 就能撑满显示区；被父约束截断会画到界外、和下面的面板叠在一起。
 * 缩一点字号总比截断或要求用户横向滚动好。
 */
@Composable
fun MathBlockView(
    block: MathBlock,
    modifier: Modifier = Modifier,
    style: MathTextStyle = rememberMathTextStyle()
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val errorText = LocalMathErrorText.current

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        val fitted = remember(
            block,
            style,
            density,
            measurer,
            errorText,
            maxWidthPx,
            maxHeightPx
        ) {
            measureFitted(block, measurer, style, density, errorText, maxWidthPx, maxHeightPx)
        }

        Canvas(
            modifier = Modifier.size(
                width = with(density) { fitted.width.toDp() },
                height = with(density) { fitted.height.toDp() }
            )
        ) {
            with(fitted) { drawAt(0f, ascent) }
        }
    }
}

/**
 * 先按原尺寸排一遍，超界就按比例缩小字号再排一遍。
 *
 * 留 2% 余量：缩小字号后各段间距（分数线粗细、上下留白）并不严格线性，
 * 恰好按比例算容易差一两个像素又被截掉一点。
 */
private fun measureFitted(
    block: MathBlock,
    measurer: TextMeasurer,
    style: MathTextStyle,
    density: Density,
    errorText: (MathError) -> String,
    maxWidthPx: Float,
    maxHeightPx: Float
): MeasuredBlock {
    val natural = measureMathBlock(block, measurer, style, density, errorText)
    val scale = fitScale(natural.width, natural.height, maxWidthPx, maxHeightPx)
    if (scale >= 0.999f) return natural

    val shrunk = style.copy(fontSize = (style.fontSize.value * scale * 0.98f).sp)
    return measureMathBlock(block, measurer, shrunk, density, errorText)
}