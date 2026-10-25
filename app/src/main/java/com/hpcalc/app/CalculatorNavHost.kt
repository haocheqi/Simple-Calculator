package com.hpcalc.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hpcalc.feature.calculator.CalculatorScreen
import com.hpcalc.feature.calculator.CalculatorUiEvent
import com.hpcalc.feature.calculator.CalculatorUiState
import com.hpcalc.feature.calculator.HistoryScreen
import com.hpcalc.feature.calculator.SettingsScreen

private const val ROUTE_CALCULATOR = "calculator"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"

/**
 * 导航图。三个页面共享同一个 ViewModel，因此设置一改、历史一进，返回计算器即时生效。
 */
@Composable
fun CalculatorNavHost(
    state: CalculatorUiState,
    onEvent: (CalculatorUiEvent) -> Unit,
    versionName: String
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_CALCULATOR) {
        composable(ROUTE_CALCULATOR) {
            CalculatorScreen(
                state = state,
                onEvent = onEvent,
                onOpenHistory = { navController.navigate(ROUTE_HISTORY) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                entries = state.history,
                onBack = { navController.popBackStack() },
                onReuse = { expression ->
                    onEvent(CalculatorUiEvent.Reuse(expression))
                    navController.popBackStack()
                },
                onDelete = { onEvent(CalculatorUiEvent.DeleteHistory(it)) },
                onTogglePin = { onEvent(CalculatorUiEvent.SetHistoryPinned(it.id, !it.pinned)) },
                onClear = { onEvent(CalculatorUiEvent.ClearHistory) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                settings = state.settings,
                versionName = versionName,
                onBack = { navController.popBackStack() },
                onTheme = { onEvent(CalculatorUiEvent.SwitchTheme(it)) },
                onAngle = { onEvent(CalculatorUiEvent.SwitchAngle(it)) },
                onDisplayMode = { onEvent(CalculatorUiEvent.SwitchDisplayMode(it)) }
            )
        }
    }
}