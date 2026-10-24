package com.hpcalc.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.hpcalc.core.data.HistoryDao
import com.hpcalc.core.data.HistoryEntity
import com.hpcalc.core.data.Settings
import com.hpcalc.core.data.SettingsRepository
import com.hpcalc.core.math.HighPrecisionEvaluator
import com.hpcalc.core.math.MatrixCalculator
import com.hpcalc.core.math.PolynomialCalculator
import com.hpcalc.core.math.formatResult
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.CalculatorMode
import com.hpcalc.core.model.CalculatorSession
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.EvaluationResult
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.SymbolicResult
import kotlinx.coroutines.flow.firstOrNull

/**
 * 阶段 1 的过渡实现：仍然把状态（表达式、模式）留在 Compose 的 `remember` 里，
 * 阶段 2 会换成 `UiState` + `UiEvent` + `SavedStateHandle`。
 * 这里先把跨次计算的 `ans` 提到 [CalculatorSession]，并让错误走结构化通道。
 */
class CalculatorViewModel(
    private val history: HistoryDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings = settingsRepository.settings
    val entries = history.observe()

    private val numeric = HighPrecisionEvaluator()
    private val symbolic = PolynomialCalculator()
    private val matrix = MatrixCalculator()
    private var session = CalculatorSession()

    suspend fun calculate(mode: CalculatorMode, input: String): String {
        val current = settingsRepository.settings.firstOrNull() ?: Settings()
        val context = EvaluationContext(angleUnit = current.angle, ans = session.ans)

        val result: Any = when (mode) {
            CalculatorMode.BASIC, CalculatorMode.SCIENTIFIC -> numeric.evaluate(input, context)
            CalculatorMode.SYMBOLIC -> {
                symbolic.setAns(session.ans)
                symbolic.simplify(input)
            }
            CalculatorMode.MATRIX -> SymbolicResult.Error(MathError.Unsupported("matrix mode uses its own controls"))
        }

        val (text, succeeded) = when (result) {
            is EvaluationResult.Success -> {
                session = session.recordAns(result.value)
                formatResult(result.value) to true
            }
            is SymbolicResult.Success -> {
                result.expression.toBigDecimalOrNull()?.let { session = session.recordAns(it) }
                result.expression to true
            }
            is EvaluationResult.Error -> errorText(result.error) to false
            is SymbolicResult.Error -> errorText(result.error) to false
            else -> errorText(MathError.Unsupported("result type")) to false
        }

        if (succeeded) {
            history.insert(HistoryEntity(mode = mode.name, expression = input, result = text))
        }
        return text
    }

    suspend fun clearHistory() = history.clear()

    suspend fun setDark(value: Boolean) = settingsRepository.setDark(value)

    suspend fun setLanguage(value: String) = settingsRepository.setLanguage(value)

    suspend fun setAngle(value: AngleUnit) {
        session = session.withAngleUnit(value)
        settingsRepository.setAngle(value)
    }

    private fun errorText(error: MathError): String = "Error: ${error.message}"

    companion object {
        /** 阶段 1 保留矩阵计算器实例，供后续矩阵界面复用。 */
        internal fun matrixCalculator(): MatrixCalculator = MatrixCalculator()

        fun factory(history: HistoryDao, settings: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    CalculatorViewModel(history, settings) as T
            }
    }
}