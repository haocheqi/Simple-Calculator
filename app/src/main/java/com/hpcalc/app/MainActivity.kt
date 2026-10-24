package com.hpcalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpcalc.app.ui.CalculatorApp
import com.hpcalc.core.data.CalculatorDatabase
import com.hpcalc.core.data.Settings
import com.hpcalc.core.data.SettingsRepository
import com.hpcalc.core.designsystem.CalculatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = CalculatorDatabase.create(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)

        setContent {
            val viewModel: CalculatorViewModel = viewModel(
                factory = CalculatorViewModel.factory(database.historyDao(), settingsRepository)
            )
            val settings by viewModel.settings.collectAsState(initial = Settings())

            CalculatorTheme(darkTheme = settings.dark) {
                Surface(Modifier.fillMaxSize()) {
                    CalculatorApp(viewModel, settings)
                }
            }
        }
    }
}