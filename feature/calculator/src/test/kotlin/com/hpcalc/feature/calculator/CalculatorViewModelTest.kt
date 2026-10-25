package com.hpcalc.feature.calculator

import com.hpcalc.core.data.HistoryDao
import com.hpcalc.core.data.HistoryEntity
import com.hpcalc.core.data.Settings
import com.hpcalc.core.data.SettingsStore
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.DisplayMode
import androidx.lifecycle.SavedStateHandle
import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import com.hpcalc.core.model.ThemeMode
import com.hpcalc.core.model.plainText
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 事件层测试。
 *
 * 这里的每一条都对应一个真实发生过的缺陷：AC 不清结果、退格不跟光标、
 * 「精确分数」设置送不到求值层、旋转后结果丢失。它们都躲过了编译器和纯函数测试，
 * 只能在 ViewModel 这一层被测出来。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val history = FakeHistoryDao()
    private val settings = FakeSettingsStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CalculatorViewModel(history, settings, handle, dispatcher)

    private fun CalculatorViewModel.type(text: String) = onEvent(CalculatorUiEvent.Append(text))

    private fun CalculatorViewModel.equals() = onEvent(CalculatorUiEvent.Evaluate)

    private fun CalculatorViewModel.resultText() = currentState.result.plainText()

    @Test
    fun `evaluating updates the result`() = runTest {
        val vm = viewModel()
        vm.type("1+1")
        vm.equals()
        assertEquals("2", vm.resultText())
        assertEquals("1+1", vm.currentState.expression)
    }

    @Test
    fun `AC clears the expression, the result and the error`() = runTest {
        val vm = viewModel()
        vm.type("1/0")
        vm.equals()
        assertTrue("expected an error block", vm.currentState.result is MathBlock.Error)

        vm.onEvent(CalculatorUiEvent.Clear)

        assertEquals("", vm.currentState.expression)
        assertEquals(0, vm.currentState.caret)
        assertEquals("0", vm.resultText())
        assertNull(vm.currentState.exactResult)
    }

    @Test
    fun `typing goes in at the caret, not at the end`() = runTest {
        val vm = viewModel()
        vm.type("1+2")
        vm.onEvent(CalculatorUiEvent.SetCaret(1))
        vm.type("9")

        // 光标在 "1" 之后，所以 9 插在 1 与 + 之间。
        assertEquals("19+2", vm.currentState.expression)
        assertEquals(2, vm.currentState.caret)
    }

    @Test
    fun `backspace deletes the character before the caret`() = runTest {
        val vm = viewModel()
        vm.type("12+34")
        vm.onEvent(CalculatorUiEvent.SetCaret(3))

        vm.onEvent(CalculatorUiEvent.Delete)

        assertEquals("1234", vm.currentState.expression)
        assertEquals(2, vm.currentState.caret)
    }

    @Test
    fun `backspace at the start leaves the expression alone`() = runTest {
        val vm = viewModel()
        vm.type("12")
        vm.onEvent(CalculatorUiEvent.SetCaret(0))

        vm.onEvent(CalculatorUiEvent.Delete)

        assertEquals("12", vm.currentState.expression)
    }

    @Test
    fun `a third can be flipped to its exact form`() = runTest {
        val vm = viewModel()
        vm.type("1/3")
        vm.equals()

        assertNotNull("1/3 should carry an exact form", vm.currentState.exactResult)
        assertFalse(vm.currentState.showsExactFraction)
        assertEquals("0.3333333333333333", vm.resultText())

        vm.onEvent(CalculatorUiEvent.ToggleResultForm)

        assertTrue(vm.currentState.showsExactFraction)
        assertTrue(vm.currentState.shownResult is MathBlock.Fraction)
    }

    @Test
    fun `an integral result offers no second form`() = runTest {
        val vm = viewModel()
        vm.type("2+3")
        vm.equals()

        assertEquals("5", vm.resultText())
        assertFalse(vm.currentState.canToggleForm)
    }

    @Test
    fun `the exact fraction display mode reaches evaluation`() = runTest {
        // 曾经的缺陷：状态里存了一份 displayMode 副本，切设置只写 DataStore，
        // 求值永远读旧值，「精确分数」这个设置因此完全失效。
        settings.settings.value = Settings(displayMode = DisplayMode.EXACT_FRACTION)

        val vm = viewModel()
        vm.type("1/3")
        vm.equals()

        assertTrue(
            "expected a true fraction, got ${vm.currentState.result}",
            vm.currentState.result is MathBlock.Fraction
        )
        assertTrue(vm.currentState.showsExactFraction)
    }

    @Test
    fun `the angle unit from settings is applied`() = runTest {
        settings.settings.value = Settings(angle = AngleUnit.DEGREES)

        val vm = viewModel()
        vm.type("sin(90)")
        vm.equals()

        val value = vm.resultText().toBigDecimal()
        assertEquals(0, value.compareTo(BigDecimal.ONE))
    }

    @Test
    fun `a successful evaluation is written to history`() = runTest {
        val vm = viewModel()
        vm.type("6*7")
        vm.equals()

        assertEquals(1, history.entries.value.size)
        assertEquals("6*7", history.entries.value.first().expression)
        assertEquals("42", history.entries.value.first().result)
    }

    @Test
    fun `a failed evaluation is not written to history`() = runTest {
        val vm = viewModel()
        vm.type("1/0")
        vm.equals()

        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun `the result survives a configuration change`() = runTest {
        // 曾经的缺陷：MathBlock 不可序列化，转屏后结果退回 0。
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.type("1/3")
        first.equals()
        assertEquals("0.3333333333333333", first.resultText())

        // 同一个 SavedStateHandle 重建，等价于旋转屏幕。
        val second = CalculatorViewModel(history, settings, handle, dispatcher)

        assertEquals("1/3", second.currentState.expression)
        assertEquals("0.3333333333333333", second.resultText())
    }

    @Test
    fun `the caret is restored at the end of a restored expression`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.type("12+34")
        first.onEvent(CalculatorUiEvent.SetCaret(2))

        val second = CalculatorViewModel(history, settings, handle, dispatcher)

        assertEquals(2, second.currentState.caret)
    }

    @Test
    fun `switching to matrix mode hides the keypad`() = runTest {
        val vm = viewModel()
        vm.onEvent(CalculatorUiEvent.SwitchMode(CalculatorMode.MATRIX))

        assertFalse(vm.currentState.showsKeypad)
        assertFalse(vm.currentState.showsFunctionStrip)
    }

    @Test
    fun `typing past the length limit is truncated`() = runTest {
        val vm = viewModel()
        vm.type("x".repeat(CalculatorViewModel.MAX_EXPRESSION))
        vm.type("1")

        assertEquals(
            CalculatorViewModel.MAX_EXPRESSION,
            vm.currentState.expression.length
        )
    }

    @Test
    fun `the matrix inverse reaches the ui as true fractions`() {
        // 端到端回归：精确分式挂在 MatrixResult.Success.exact 上，引擎曾经绕开它，
        // 界面因此只看到小数。断言落在 ViewModel 的输出上，才是用户真正看到的东西。
        val vm = viewModel()
        vm.onEvent(CalculatorUiEvent.SwitchMode(CalculatorMode.MATRIX))
        listOf("1", "2", "3", "4").forEachIndexed { index, value ->
            vm.onEvent(
                CalculatorUiEvent.SetMatrixCell(
                    side = MatrixSide.LEFT,
                    row = index / 2,
                    column = index % 2,
                    value = value
                )
            )
        }
        vm.onEvent(CalculatorUiEvent.SetMatrixOperation(MatrixOperationKind.INVERSE))
        vm.onEvent(CalculatorUiEvent.EvaluateMatrix)

        assertEquals("−2, 1; 3/2, −1/2", vm.currentState.result.describe())
    }

    @Test
    fun `matrix cells reject anything that is not a number`() {
        val vm = viewModel()
        vm.onEvent(CalculatorUiEvent.SetMatrixCell(MatrixSide.LEFT, 0, 0, "1 a.5x"))
        vm.onEvent(CalculatorUiEvent.SetMatrixCell(MatrixSide.LEFT, 0, 1, "abc"))

        assertEquals("1.5", vm.currentState.matrix.left[0][0])
        assertEquals("", vm.currentState.matrix.left[0][1])
    }

    @Test
    fun `an unusable matrix reports an error instead of doing nothing`() {
        // 单元格被过滤后仍可能留下裸 "-"，解析不了。按「计算」必须有反馈。
        val vm = viewModel()
        vm.onEvent(CalculatorUiEvent.SwitchMode(CalculatorMode.MATRIX))
        vm.onEvent(CalculatorUiEvent.SetMatrixCell(MatrixSide.LEFT, 0, 0, "-"))
        vm.onEvent(CalculatorUiEvent.SetMatrixOperation(MatrixOperationKind.TRANSPOSE))
        vm.onEvent(CalculatorUiEvent.EvaluateMatrix)

        val block = vm.currentState.result
        assertTrue("expected an error, got $block", block is MathBlock.Error)
        val error = (block as MathBlock.Error).error
        assertTrue(error is MathError.ParseError)
        assertEquals(MathIssue.InvalidMatrixCell, (error as MathError.ParseError).issue)
    }
}

/** 内存版历史表，行为与 Room 查询保持一致（按置顶、时间排序）。 */
private class FakeHistoryDao : HistoryDao {

    val entries = MutableStateFlow<List<HistoryEntity>>(emptyList())

    private var nextId = 1L

    override fun observe(): Flow<List<HistoryEntity>> = entries

    override suspend fun insert(item: HistoryEntity) {
        entries.value = (entries.value + item.copy(id = nextId++)).sortedWith(
            compareByDescending<HistoryEntity> { it.pinned }.thenByDescending { it.createdAt }
        )
    }

    override suspend fun clear() {
        entries.value = emptyList()
    }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        entries.value = entries.value
            .map { if (it.id == id) it.copy(pinned = pinned) else it }
            .sortedWith(compareByDescending<HistoryEntity> { it.pinned }.thenByDescending { it.createdAt })
    }
}

/** 内存版设置，替掉需要 Android Context 的 DataStore 实现。 */
private class FakeSettingsStore : SettingsStore {

    override val settings = MutableStateFlow(Settings())

    override suspend fun setTheme(value: ThemeMode) {
        settings.value = settings.value.copy(theme = value)
    }

    override suspend fun setAngle(value: AngleUnit) {
        settings.value = settings.value.copy(angle = value)
    }

    override suspend fun setDisplayMode(value: DisplayMode) {
        settings.value = settings.value.copy(displayMode = value)
    }
}

/** 把呈现树压成紧凑文本，便于断言结构。 */
private fun MathBlock.describe(): String = when (this) {
    is MathBlock.Number -> literal
    is MathBlock.Operator -> text
    is MathBlock.Fraction -> "${numerator.describe()}/${denominator.describe()}"
    is MathBlock.Row -> children.joinToString("") { it.describe() }
    is MathBlock.Matrix -> rows.joinToString("; ") { row -> row.joinToString(", ") { it.describe() } }
    else -> "<other>"
}
