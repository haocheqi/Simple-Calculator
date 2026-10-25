package com.hpcalc.feature.calculator

import com.hpcalc.core.designsystem.CalcKeyStyle

/** 按键行为。 */
enum class KeyAction { DELETE, CLEAR, EVALUATE }

/**
 * 一个按键的定义。
 *
 * [label] 是显示文本，[insert] 是真正插入表达式的字符——两者刻意分开：
 * 界面显示 `−`/`×`/`÷` 这类数学符号，解析器收到的仍是 `-`/`*`/`/`。
 * [weight] 用于让 `0` 这类键占两格。
 */
data class KeySpec(
    val label: String,
    val insert: String? = null,
    val action: KeyAction? = null,
    val style: CalcKeyStyle = CalcKeyStyle.NUMBER,
    val descriptionRes: Int? = null,
    val weight: Float = 1f
)

private fun digit(value: Int) = KeySpec(label = value.toString(), insert = value.toString())

private val clearKey = KeySpec(
    label = "AC",
    action = KeyAction.CLEAR,
    style = CalcKeyStyle.DANGER,
    descriptionRes = R.string.cd_clear
)

private val backspaceKey = KeySpec(
    label = "⌫",
    action = KeyAction.DELETE,
    style = CalcKeyStyle.DANGER,
    descriptionRes = R.string.cd_backspace
)

private val equalsKey = KeySpec(
    label = "=",
    action = KeyAction.EVALUATE,
    style = CalcKeyStyle.EQUAL,
    descriptionRes = R.string.cd_equals
)

private fun operator(label: String, insert: String) =
    KeySpec(label = label, insert = insert, style = CalcKeyStyle.OPERATOR)

private fun function(label: String, insert: String) =
    KeySpec(label = label, insert = insert, style = CalcKeyStyle.FUNCTION)

/**
 * 主键盘：4 列 × 5 行，基础模式与科学模式共用。
 *
 * 布局取舍：
 * - 第一行放 `AC` 与退格。退格是最高频的纠错键，放右下角容易被 `=` 误触，放顶行反而安全。
 * - 右列固定是运算符（`÷ × − +`），`=` 紧挨在 `+` 左边——保持运算符成列，肌肉记忆才不会断。
 * - `0` 不再横跨两格：需要腾出位置给退格，而 4×5=20 格刚好放下 20 个键。
 */
val mainKeypad: List<List<KeySpec>> = listOf(
    listOf(clearKey, backspaceKey, operator("(", "("), operator(")", ")")),
    listOf(digit(7), digit(8), digit(9), operator("÷", "/")),
    listOf(digit(4), digit(5), digit(6), operator("×", "*")),
    listOf(digit(1), digit(2), digit(3), operator("−", "-")),
    listOf(
        KeySpec(label = "0", insert = "0"),
        KeySpec(label = ".", insert = "."),
        equalsKey,
        operator("+", "+")
    )
)

/** 科学模式的函数条。横向滚动，不挤占键盘空间。 */
val scientificFunctions: List<KeySpec> = listOf(
    function("sin", "sin("),
    function("cos", "cos("),
    function("tan", "tan("),
    function("asin", "asin("),
    function("acos", "acos("),
    function("atan", "atan("),
    function("ln", "ln("),
    function("log", "log("),
    function("√", "√("),
    function("x²", "^2"),
    function("xʸ", "^"),
    function("π", "π"),
    function("e", "e"),
    function("ans", "ans"),
    function("n!", "!"),
    function("%", "%"),
    function("abs", "abs(")
)

/** 符号模式的函数条。 */
val symbolicFunctions: List<KeySpec> = listOf(
    function("x", "x"),
    function("y", "y"),
    function("x²", "^2"),
    function("xʸ", "^"),
    function("(", "("),
    function(")", ")"),
    function("×", "*"),
    function("÷", "/"),
    function("+", "+"),
    function("−", "-"),
    function("π", "π"),
    function("ans", "ans")
)