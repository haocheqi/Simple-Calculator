package com.hpcalc.core.mathui

import androidx.compose.foundation.Canvas
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
 * 尺寸由内容决定；需要横向滚动时由调用方套 `horizontalScroll`。
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
    val measured = remember(block, style, density, measurer, errorText) {
        measureMathBlock(block, measurer, style, density, errorText)
    }

    Canvas(
        modifier = modifier.size(
            width = with(density) { measured.width.toDp() },
            height = with(density) { measured.height.toDp() }
        )
    ) {
        with(measured) { drawAt(0f, ascent) }
    }
}