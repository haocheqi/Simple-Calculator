package com.hpcalc.feature.calculator

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
            // Android 15 起强制 edge-to-edge，不主动让出系统栏就会画到状态栏底下，
            // 顶部按钮既难点中、也容易和通知栏手势打架。
            .windowInsetsPadding(WindowInsets.systemBars)
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
                onEvent = onEvent,
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
                onEvent = onEvent,
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
private fun DisplayArea(
    state: CalculatorUiState,
    onEvent: (CalculatorUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        ExpressionLine(state = state, onEvent = onEvent)
        Spacer(Modifier.height(CalcTokens.SectionSpacing))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 只有结果同时具备小数与分数两种形态时才给这个开关，避免点了没反应。
            if (state.canToggleForm) {
                FormToggle(
                    showsFraction = state.showsExactFraction,
                    onClick = { onEvent(CalculatorUiEvent.ToggleResultForm) }
                )
                Spacer(Modifier.width(CalcTokens.KeyGap))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MathBlockView(block = state.shownResult, style = rememberMathTextStyle(34.sp))
                }
            }
        }
    }
}

/**
 * 算式行：点击任意位置把光标挪过去。
 *
 * 光标位置由 [TextLayoutResult.getOffsetForPosition] 从点击坐标反推，所以不需要额外的方向键，
 * 也不用把算式换成可编辑输入框——那样会招来系统键盘，和自带键盘打架。
 * 光标停在中间时画一条竖线，停在末尾时不画（末尾是隐式的）。
 */
@Composable
private fun ExpressionLine(state: CalculatorUiState, onEvent: (CalculatorUiEvent) -> Unit) {
    val caretColor = MaterialTheme.colorScheme.primary
    val placeholder = stringResource(R.string.display_placeholder)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotated = if (state.expression.isEmpty()) {
        AnnotatedString(placeholder)
    } else {
        buildAnnotatedString {
            val caret = state.caret.coerceIn(0, state.expression.length)
            append(state.expression.substring(0, caret))
            if (caret < state.expression.length) {
                withStyle(SpanStyle(color = caretColor)) { append("|") }
            }
            append(state.expression.substring(caret))
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = if (state.expression.isEmpty()) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        onTextLayout = { layoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(state.expression) {
                detectTapGestures { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    onEvent(CalculatorUiEvent.SetCaret(layout.getOffsetForPosition(offset)))
                }
            }
    )
}

/** S⇔D：在小数与精确分数之间切换当前结果。 */
@Composable
private fun FormToggle(showsFraction: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "S⇔D",
            style = MaterialTheme.typography.labelMedium,
            color = if (showsFraction) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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

/**
 * 函数条。
 *
 * 用 [FlowRow] 折行而不是横向滚动：科学模式有 17 个函数，一列滚动的条要滑好几次才找得到
 * `abs`，而横向滚动在手机上没有可见提示。折成 3 行后所有函数一眼可及。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FunctionStrip(specs: List<KeySpec>, onEvent: (CalculatorUiEvent) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap),
        verticalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)
    ) {
        specs.forEach { spec ->
            CalcKey(
                label = spec.label,
                onClick = { dispatch(spec, onEvent) },
                modifier = Modifier.width(CalcTokens.FunctionKeyWidth),
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