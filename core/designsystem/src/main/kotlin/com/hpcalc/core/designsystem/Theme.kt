package com.hpcalc.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 设计系统的语义色板。阶段 2 会把界面里散落的硬编码颜色全部收敛到这里。
private val Orange = Color(0xFFF5A623)
private val DeepOrange = Color(0xFFD97706)
private val Ink = Color(0xFF171717)
private val Paper = Color(0xFFFFFBF5)

private val LightCalculatorColors = lightColorScheme(
    primary = DeepOrange,
    onPrimary = Color.White,
    primaryContainer = Orange,
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Color.White,
    surface = Paper,
    surfaceVariant = Color(0xFFFFF1D6),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF5F5548)
)

private val DarkCalculatorColors = darkColorScheme(
    primary = Orange,
    onPrimary = Ink,
    primaryContainer = DeepOrange,
    onPrimaryContainer = Color.White,
    secondary = Orange,
    onSecondary = Ink,
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF2A2722),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFD6C7B4)
)

@Composable
fun CalculatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkCalculatorColors else LightCalculatorColors,
        content = content
    )
}