package com.hpcalc.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpcalc.app.CalculatorViewModel
import com.hpcalc.core.data.HistoryEntity
import com.hpcalc.core.data.Settings
import com.hpcalc.core.math.MatrixCalculator
import com.hpcalc.core.math.PolynomialCalculator
import com.hpcalc.core.model.*
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorApp(vm: CalculatorViewModel, settings: Settings) {
    var mode by remember { mutableStateOf(CalculatorMode.BASIC) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val history by vm.entries.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val zh = settings.language == "zh"
    Scaffold(topBar = { TopAppBar(
        title = { Text(if (zh) "计算器" else "Calculator", fontWeight = FontWeight.SemiBold) },
        actions = {
            Text(if (zh) "中文" else "EN", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { scope.launch { vm.setLanguage(if (zh) "en" else "zh") } }) { Text(if (zh) "EN" else "中") }
            IconButton(onClick = { scope.launch { vm.setDark(!settings.dark) } }) { Text(if (settings.dark) "☀" else "☾") }
        }
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeSelector(mode, zh) { mode = it; input = ""; output = "" }
            if (mode == CalculatorMode.MATRIX) MatrixPanel(zh) else {
                DisplayCard(input, output, zh)
                if (mode == CalculatorMode.SYMBOLIC) { Text(if (zh) "Super 风格公式编译：输入 x、y、z、ans，支持隐式乘法" else "Formula compiler: x, y, z, ans and implicit multiplication", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); FormulaCompilerCard(input, zh) }
                CalculatorKeypad(mode, input, { input = it }, { input = ""; output = "" }, { scope.launch { output = vm.calculate(mode, input) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (zh) "历史记录" else "History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { vm.clearHistory() } }) { Icon(Icons.Default.Delete, if (zh) "清空历史" else "Clear history") }
                OutlinedButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) if (zh) "收起" else "Hide" else if (zh) "查看" else "Show") }
            }
            if (showHistory) HistoryList(history, zh)
        }
    }
}

@Composable
private fun ModeSelector(selected: CalculatorMode, zh: Boolean, onSelected: (CalculatorMode) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalculatorMode.values().forEach { mode -> FilterChip(selected == mode, { onSelected(mode) }, label = { Text(mode.label(zh)) }) }
    }
}
private fun CalculatorMode.label(zh: Boolean) = if (zh) when (this) {
    CalculatorMode.BASIC -> "普通"; CalculatorMode.SCIENTIFIC -> "科学"; CalculatorMode.SYMBOLIC -> "符号"; CalculatorMode.MATRIX -> "矩阵"
} else when (this) {
    CalculatorMode.BASIC -> "Basic"; CalculatorMode.SCIENTIFIC -> "Scientific"; CalculatorMode.SYMBOLIC -> "Symbolic"; CalculatorMode.MATRIX -> "Matrix"
}

@Composable
private fun DisplayCard(input: String, output: String, zh: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.End) {
            Text(input.ifBlank { if (zh) "输入表达式" else "Enter an expression" }, color = Color(0xFFB9A990), fontSize = 17.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(output.ifBlank { "0" }, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), color = if (output.startsWith("Error")) Color(0xFFFF8A80) else Color(0xFFFFC857))
        }
    }
}

@Composable
private fun CalculatorKeypad(mode: CalculatorMode, input: String, onInput: (String) -> Unit, onClear: () -> Unit, onEvaluate: () -> Unit) {
    var shifted by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    val scientificTop = when (page) { 0 -> listOf(if (shifted) "asin(" else "sin(", if (shifted) "acos(" else "cos(", if (shifted) "atan(" else "tan(", if (shifted) "e^" else "ln(", if (shifted) "10^" else "log("); 1 -> listOf("sinh(", "cosh(", "tanh(", "abs(", "sqrt("); 2 -> listOf("!", "Ans", "π", "e", "mod"); else -> listOf("floor(", "ceil(", "round(", "exp(", "DRG") }
    val keys = if (mode == CalculatorMode.SCIENTIFIC) listOf(
        scientificTop, listOf("2nd", "PAGE", "Ans", "π", "e"), listOf("7", "8", "9", "÷", "("), listOf("4", "5", "6", "×", ")"), listOf("1", "2", "3", "−", "^"), listOf("AC", "0", ".", "+", "=")
    ) else if (mode == CalculatorMode.SYMBOLIC) listOf(
        listOf("AC", "⌫", "Ans", "(", ")"), listOf("7", "8", "9", "×", "x"), listOf("4", "5", "6", "+", "y"), listOf("1", "2", "3", "−", "z"), listOf("0", ".", "^", "=", "*")
    ) else listOf(
        listOf("AC", "⌫", "Ans", "(", ")"), listOf("7", "8", "9", "÷", "−"), listOf("4", "5", "6", "×", "+"), listOf("1", "2", "3", ".", "^"), listOf("0", "=", "!", "%")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        keys.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { key -> KeyButton(key, Modifier.weight(if (key in setOf("0","1","2","3","4","5","6","7","8","9",".")) 1.18f else 0.9f), key in setOf("÷", "×", "−", "+", "=", "^", "!", "%", "2nd"), key == "AC") {
                when (key) { "=" -> onEvaluate(); "AC" -> onClear(); "2nd" -> shifted = !shifted; "PAGE" -> page = (page + 1) % 4; "DRG" -> Unit; "⌫" -> onInput(input.dropLast(1)); "÷" -> onInput(input + "/"); "×" -> onInput(input + "*"); "−" -> onInput(input + "-"); "Ans" -> onInput(input + "ans"); "√(" -> onInput(input + "sqrt("); "mod" -> onInput(input + "%"); else -> onInput(input + key) }
            } }
        } }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, isOperator: Boolean, isClear: Boolean, onClick: () -> Unit) {
    Button(onClick, modifier.height(54.dp).semantics { contentDescription = label }, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = when { isClear -> Color(0xFFFFD7D2); isOperator -> Color(0xFFF5A623); else -> Color(0xFF242424) }, contentColor = when { isClear -> Color(0xFF8C1D18); isOperator -> Color(0xFF171717); else -> Color.White })) {
        if (label == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace") else Text(label, fontSize = if (label.length > 3) 13.sp else 18.sp, maxLines = 1)
    }
}

@Composable
private fun FormulaCompilerCard(input: String, zh: Boolean) {
    var compiled by remember(input) { mutableStateOf("") }
    var substitution by remember(input) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (zh) "公式编译" else "Formula compiler", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("x · y · z · ans", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (zh) "编译结果会显示在这里；代入格式：表达式, x=2" else "Compiled output appears here; substitution: expression, x=2", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { compiled = when(val r = PolynomialCalculator().simplify(input)) { is SymbolicResult.Success -> r.expression; is SymbolicResult.Error -> r.error.message } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 7.dp)) { Text(if (zh) "编译 / 化简" else "Compile", fontSize = 13.sp) }
                OutlinedButton(onClick = {
                    substitution = try {
                        val parts = input.split(",", ";").map { it.trim() }
                        val assignment = parts.firstOrNull { it.matches(Regex("[A-Za-z]\\s*=\\s*-?[0-9.]+")) }
                        val expr = parts.lastOrNull { !it.matches(Regex("[A-Za-z]\\s*=\\s*-?[0-9.]+")) } ?: input
                        if (assignment == null) "请使用 x=2" else {
                            val n=assignment.substringBefore("=").trim(); val v=assignment.substringAfter("=").trim().toBigDecimal()
                            when(val r=PolynomialCalculator().substitute(expr, mapOf(n to v))) { is SymbolicResult.Success -> r.expression; is SymbolicResult.Error -> r.error.message }
                        }
                    } catch(e: Exception) { e.message ?: "Invalid substitution" }
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 7.dp)) { Text(if (zh) "代入求值" else "Substitute", fontSize = 13.sp) }
            }
            if (compiled.isNotBlank()) Text("f(x) = $compiled", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF5A623))
            if (substitution.isNotBlank()) Text("→ $substitution", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable
private fun HistoryList(history: List<HistoryEntity>, zh: Boolean) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (history.isEmpty()) Text(if (zh) "还没有计算记录" else "No calculations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        history.take(8).forEach { item -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(12.dp)) { Text(item.expression, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(item.result, fontSize = 20.sp, fontWeight = FontWeight.Medium) } } }
    }
}

@Composable
private fun MatrixPanel(zh: Boolean) {
    var size by remember { mutableStateOf(2) }
    var left by remember { mutableStateOf(List(2) { List(2) { "0" } }) }
    var right by remember { mutableStateOf(List(2) { List(2) { "0" } }) }
    var operation by remember { mutableStateOf("det") }
    var result by remember { mutableStateOf<MatrixResult?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (zh) "矩阵编辑器" else "Matrix editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..6).forEach { n -> FilterChip(size == n, { size = n; left = List(n) { List(n) { "0" } }; right = List(n) { List(n) { "0" } }; result = null }, label = { Text("$n×$n") }) } }
        MatrixGrid(if (zh) "矩阵 A" else "Matrix A", left) { left = it }
        if (operation == "add" || operation == "sub" || operation == "mul") MatrixGrid(if (zh) "矩阵 B" else "Matrix B", right) { right = it }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("det", "add", "sub", "mul", "transpose", "inverse", "rank").forEach { op -> FilterChip(operation == op, { operation = op }, label = { Text(when (op) { "det" -> if (zh) "行列式" else "det"; "add" -> "+"; "sub" -> "−"; "mul" -> "×"; "transpose" -> if (zh) "转置" else "T"; "inverse" -> "A⁻¹"; else -> if (zh) "秩" else "rank" }) }) }
        }
        Button(onClick = { result = matrixOperation(left, right, operation) }, modifier = Modifier.fillMaxWidth()) { Text(if (zh) "计算矩阵" else "Calculate matrix") }
        result?.let { MatrixResultBlock(it, zh) }
    }
}
@Composable private fun MatrixGrid(title: String, cells: List<List<String>>, onChange: (List<List<String>>) -> Unit) { Text(title, style = MaterialTheme.typography.labelLarge); cells.forEachIndexed { r, row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEachIndexed { c, value -> OutlinedTextField(value, { x -> onChange(cells.mapIndexed { rr, old -> if (rr == r) old.mapIndexed { cc, oldValue -> if (cc == c) x else oldValue } else old }) }, Modifier.weight(1f).height(58.dp), singleLine = true) } } } }
@Composable private fun MatrixResultBlock(result: MatrixResult, zh: Boolean) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { when (result) { is MatrixResult.Success -> { Text(if (zh) "结果矩阵" else "Result matrix", style = MaterialTheme.typography.labelLarge); MathBlockView(result.matrix.toMathBlock()) }; is MatrixResult.Scalar -> MathBlockView(MathBlock.Scalar(result.value.stripTrailingZeros().toPlainString())); is MatrixResult.Text -> MathBlockView(MathBlock.Text(result.value)); is MatrixResult.Error -> MathBlockView(MathBlock.Error(result.error.message)) } } } }
@Composable private fun MathBlockView(block: MathBlock) { when (block) { is MathBlock.Scalar -> Text(block.value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); is MathBlock.Text -> Text(block.value, fontSize = 20.sp); is MathBlock.Error -> Text("Error: ${block.message}", color = MaterialTheme.colorScheme.error); is MathBlock.Matrix -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { block.rows.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { row.forEach { cell -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { MathBlockView(cell) } } } } }; is MathBlock.Sequence -> Row { block.children.forEach { MathBlockView(it) } }; is MathBlock.Fraction -> Column(horizontalAlignment = Alignment.CenterHorizontally) { MathBlockView(block.numerator); Divider(Modifier.width(40.dp)); MathBlockView(block.denominator) }; is MathBlock.Superscript -> Row(verticalAlignment = Alignment.Top) { MathBlockView(block.base); Text(block.exponent.toString(), fontSize = 12.sp) }; is MathBlock.Function -> Row { Text(block.name); MathBlockView(block.argument) } } }
private fun matrixOperation(a: List<List<String>>, b: List<List<String>>, op: String): MatrixResult = try { val left = Matrix(a.map { it.map(String::toBigDecimal) }); val right = Matrix(b.map { it.map(String::toBigDecimal) }); val operation = when (op) { "det" -> MatrixOperation.Determinant(left); "add" -> MatrixOperation.Add(left, right); "sub" -> MatrixOperation.Subtract(left, right); "mul" -> MatrixOperation.Multiply(left, right); "transpose" -> MatrixOperation.Transpose(left); "inverse" -> MatrixOperation.Inverse(left); else -> MatrixOperation.Rank(left) }; MatrixCalculator().evaluate(operation) } catch (e: Exception) { MatrixResult.Error(MathError.ParseError(e.message ?: "Invalid matrix")) }



