package com.hpcalc.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.ThemeMode
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/**
 * 用户设置。
 *
 * 阶段 2 删掉了 v2.0 的 `language` 字段：它只驱动了一个布尔变量，
 * 并未真正切换语言。v3.0 依靠系统语言 + `values-zh-rCN` 资源解析，
 * 系统语言一换、应用重进即整体生效，不需要自维护的开关。
 */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val angle: AngleUnit = AngleUnit.RADIANS,
    val displayMode: DisplayMode = DisplayMode.DECIMAL
)

class SettingsRepository(private val context: Context) {

    val settings = context.settingsStore.data.map { prefs ->
        Settings(
            // 非法值（例如降级后残留）静默回退，不让解析失败炸掉启动流程。
            theme = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            angle = runCatching { AngleUnit.valueOf(prefs[Keys.ANGLE] ?: "RADIANS") }
                .getOrDefault(AngleUnit.RADIANS),
            displayMode = runCatching { DisplayMode.valueOf(prefs[Keys.DISPLAY_MODE] ?: "DECIMAL") }
                .getOrDefault(DisplayMode.DECIMAL)
        )
    }

    // 显式声明 Unit：`edit { }` 的表达式体会把返回类型推断成 Preferences，
    // 那个类型不该出现在跨模块 API 上。
    suspend fun setTheme(value: ThemeMode) {
        context.settingsStore.edit { it[Keys.THEME] = value.name }
    }

    suspend fun setAngle(value: AngleUnit) {
        context.settingsStore.edit { it[Keys.ANGLE] = value.name }
    }

    suspend fun setDisplayMode(value: DisplayMode) {
        context.settingsStore.edit { it[Keys.DISPLAY_MODE] = value.name }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ANGLE = stringPreferencesKey("angle")
        val DISPLAY_MODE = stringPreferencesKey("displayMode")
    }
}