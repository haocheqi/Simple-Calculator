package com.hpcalc.feature.calculator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpcalc.core.designsystem.CalcKey
import com.hpcalc.core.designsystem.CalcKeyStyle
import com.hpcalc.core.designsystem.CalcTokens
import com.hpcalc.core.mathui.MathBlockView
import com.hpcalc.core.mathui.rememberMathTextStyle
import com.hpcalc.core.model.CalculatorMode

/**
 * 计算器主界面。
 *
 * 无状态：所有可见数据来自 [CalculatorUiState]，所有交互以 [CalculatorUiEvent] 上抛。
 * 这样界面本身可以被任意状态渲染，也能在没有设备的情况下用状态驱动做快照检查。
 *
 * 布局策略：显示区吃掉剩余高度，键盘贴底固定；
 * 基础模式只有 5 行键盘，科学/符号模式才在键盘上方展开函数条。
 */
@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onEvent: (CalculatorUiEvent) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CalcTokens.ScreenPadding)
            .padding(top = CalcTokens.ScreenPadding)
    ) {
        CalculatorHeader(onOpenHistory = onOpenHistory, onOpenSettings = onOpenSettings)
        ModeSelector(
            current = state.mode,
            onSelect = { onEvent(CalculatorUiEvent.SwitchMode(it)) }
        )
        Spacer(Modifier.height(CalcTokens.SectionSpacing))

        if (state.mode == CalculatorMode.MATRIX) {
            DisplayArea(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp, max = 160.dp)
            )
            Spacer(Modifier.height(CalcTokens.SectionSpacing))
            MatrixPanel(
                matrix = state.matrix,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            DisplayArea(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            if (state.showsSubstitutionStrip) {
                Spacer(Modifier.height(CalcTokens.SectionSpacing))
                SubstitutionStrip(
                    variable = state.substituteVariable,
                    value = state.substituteValue,
                    enabled = state.canSubstitute,
                    onVariableChange = { onEvent(CalculatorUiEvent.SetSubstituteVariable(it)) },
                    onValueChange = { onEvent(CalculatorUiEvent.SetSubstituteValue(it)) },
                    onSubstitute = { onEvent(CalculatorUiEvent.Substitute) }
                )
            }
            if (state.showsFunctionStrip) {
                Spacer(Modifier.height(CalcTokens.SectionSpacing))
                FunctionStrip(
                    specs = if (state.mode == CalculatorMode.SYMBOLIC) {
                        symbolicFunctions
                    } else {
                        scientificFunctions
                    },
                    onEvent = onEvent
                )
            }
            Spacer(Modifier.height(CalcTokens.SectionSpacing))
            Keypad(onEvent = onEvent)
            Spacer(Modifier.height(CalcTokens.ScreenPadding))
        }
    }
}

@Composable
private fun CalculatorHeader(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenHistory) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = stringResource(R.string.action_history)
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.action_settings)
            )
        }
    }
}

@Composable
private fun ModeSelector(current: CalculatorMode, onSelect: (CalculatorMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
    ) {
        CalculatorMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.labelRes())) }
            )
        }
    }
}

private fun CalculatorMode.labelRes(): Int = when (this) {
    CalculatorMode.BASIC -> R.string.mode_basic
    CalculatorMode.SCIENTIFIC -> R.string.mode_scientific
    CalculatorMode.SYMBOLIC -> R.string.mode_symbolic
    CalculatorMode.MATRIX -> R.string.mode_matrix
}

/**
 * 显示区：上面是算式原文，下面是渲染后的结果。
 *
 * 结果走 [MathBlockView]，由自定义排版引擎绘制基线、分数与矩阵定界符；
 * 这里不拼字符串，也不做任何格式化——格式化在 `:core:math` 已完成。
 */
@Composable
private fun DisplayArea(state: CalculatorUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = state.expression.ifEmpty { stringResource(R.string.display_placeholder) },
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.expression.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(CalcTokens.SectionSpacing))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MathBlockView(block = state.result, style = rememberMathTextStyle(34.sp))
            }
        }
    }
}

/**
 * 符号模式的代入条：`变量 = 数值` 加一个「代入」。
 *
 * 两个输入都只是文本，代入值允许写表达式（`π/2`、`1+1`）；
 * 变量名校验与表达式解析都在 [com.hpcalc.core.math.CalculatorEngine.substitute] 里做，
 * 界面不猜语法、也不预先拦截——错误由引擎回一个 `MathBlock.Error` 显示在结果区。
 */
@Composable
private fun SubstitutionStrip(
    variable: String,
    value: String,
    enabled: Boolean,
    onVariableChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSubstitute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
    ) {
        OutlinedTextField(
            value = variable,
            onValueChange = onVariableChange,
            modifier = Modifier.width(92.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.substitute_variable)) },
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "=",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.substitute_value)) },
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onSubstitute,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.substitute_action))
        }
    }
}

@Composable
private fun FunctionStrip(specs: List<KeySpec>, onEvent: (CalculatorUiEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
    ) {
        specs.forEach { spec ->
            CalcKey(
                label = spec.label,
                onClick = { dispatch(spec, onEvent) },
                modifier = Modifier.width(56.dp),
                style = CalcKeyStyle.FUNCTION,
                description = spec.descriptionRes?.let { stringResource(it) },
                minHeight = CalcTokens.FunctionStripHeight
            )
        }
    }
}

@Composable
private fun Keypad(onEvent: (CalculatorUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
    ) {
        mainKeypad.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
            ) {
                row.forEach { spec ->
                    CalcKey(
                        label = spec.label,
                        onClick = { dispatch(spec, onEvent) },
                        modifier = Modifier.weight(spec.weight),
                        style = spec.style,
                        description = spec.descriptionRes?.let { stringResource(it) }
                    )
                }
            }
        }
    }
}

private fun dispatch(spec: KeySpec, onEvent: (CalculatorUiEvent) -> Unit) {
    when (spec.action) {
        KeyAction.CLEAR -> onEvent(CalculatorUiEvent.Clear)
        KeyAction.DELETE -> onEvent(CalculatorUiEvent.Delete)
        KeyAction.EVALUATE -> onEvent(CalculatorUiEvent.Evaluate)
        null -> spec.insert?.let { onEvent(CalculatorUiEvent.Append(it)) }
    }
}