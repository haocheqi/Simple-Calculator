package com.hpcalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpcalc.core.designsystem.CalculatorTheme
import com.hpcalc.core.model.ThemeMode
import com.hpcalc.feature.calculator.CalculatorViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as CalculatorApplication).container

        setContent {
            // ViewModel 挂在 Activity 上，三个路由共享同一份状态。
            val viewModel: CalculatorViewModel = viewModel(
                factory = CalculatorViewModel.factory(
                    history = container.historyDao,
                    settings = container.settingsRepository
                )
            )
            val state by viewModel.state.collectAsState()

            val darkTheme = when (state.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            CalculatorTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    CalculatorNavHost(
                        state = state,
                        onEvent = viewModel::onEvent,
                        versionName = BuildConfig.VERSION_NAME
                    )
                }
            }
        }
    }
}