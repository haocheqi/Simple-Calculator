package com.hpcalc.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hpcalc.core.model.AngleUnit
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

data class Settings(
    val dark: Boolean = false,
    val language: String = "zh",
    val angle: AngleUnit = AngleUnit.RADIANS
)

class SettingsRepository(private val context: Context) {

    val settings = context.settingsStore.data.map { prefs ->
        Settings(
            dark = prefs[Keys.DARK] ?: false,
            language = prefs[Keys.LANGUAGE] ?: "zh",
            // 非法值（例如降级后残留）静默回退到弧度制，不让解析失败炸掉启动流程。
            angle = runCatching { AngleUnit.valueOf(prefs[Keys.ANGLE] ?: "RADIANS") }
                .getOrDefault(AngleUnit.RADIANS)
        )
    }

    // 显式声明 Unit：`edit { }` 的表达式体会把返回类型推断成 Preferences，
    // 那个类型不该出现在跨模块 API 上。
    suspend fun setDark(value: Boolean) {
        context.settingsStore.edit { it[Keys.DARK] = value }
    }

    suspend fun setLanguage(value: String) {
        context.settingsStore.edit { it[Keys.LANGUAGE] = value }
    }

    suspend fun setAngle(value: AngleUnit) {
        context.settingsStore.edit { it[Keys.ANGLE] = value.name }
    }

    private object Keys {
        val DARK = booleanPreferencesKey("dark")
        val LANGUAGE = stringPreferencesKey("language")
        val ANGLE = stringPreferencesKey("angle")
    }
}