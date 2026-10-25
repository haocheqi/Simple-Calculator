package com.hpcalc.feature.calculator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hpcalc.core.data.Settings
import com.hpcalc.core.designsystem.CalcTokens
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.ThemeMode

/** 设置页。全部选项都直接写进 DataStore，重启后保持。 */
@Composable
fun SettingsScreen(
    settings: Settings,
    versionName: String,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAngle: (AngleUnit) -> Unit,
    onDisplayMode: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CalcTokens.ScreenPadding)
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            OptionChips(
                options = ThemeMode.entries,
                selected = settings.theme,
                label = { stringResource(it.labelRes()) },
                onSelect = onTheme
            )

            SectionTitle(stringResource(R.string.settings_angle))
            OptionChips(
                options = AngleUnit.entries,
                selected = settings.angle,
                label = { stringResource(it.labelRes()) },
                onSelect = onAngle
            )

            SectionTitle(stringResource(R.string.settings_display))
            OptionChips(
                options = DisplayMode.entries,
                selected = settings.displayMode,
                label = { stringResource(it.labelRes()) },
                onSelect = onDisplayMode
            )

            SectionTitle(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_precision, 50, 16),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(CalcTokens.ScreenPadding))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(CalcTokens.ScreenPadding))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(CalcTokens.KeyGap))
}

@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun AngleUnit.labelRes(): Int = when (this) {
    AngleUnit.RADIANS -> R.string.angle_radians
    AngleUnit.DEGREES -> R.string.angle_degrees
    AngleUnit.GRADIANS -> R.string.angle_gradians
}

private fun DisplayMode.labelRes(): Int = when (this) {
    DisplayMode.DECIMAL -> R.string.display_decimal
    DisplayMode.ENGINEERING -> R.string.display_engineering
    DisplayMode.EXACT_FRACTION -> R.string.display_exact_fraction
}