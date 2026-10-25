package com.hpcalc.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.DisplayMode
import com.hpcalc.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
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

/**
 * 设置的读写接口。
 *
 * 抽出来是为了让 ViewModel 可以在纯 JVM 上被测：`SettingsRepository` 需要 Android `Context`，
 * 而事件层恰恰是历史上 bug 最多的地方（AC 不清结果、光标错位、显示模式没送到求值层），
 * 没有测试就只能靠真机试。测试用内存实现替换即可。
 */
interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun setTheme(value: ThemeMode)

    suspend fun setAngle(value: AngleUnit)

    suspend fun setDisplayMode(value: DisplayMode)
}

class SettingsRepository(private val context: Context) : SettingsStore {

    override val settings = context.settingsStore.data.map { prefs ->
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
    override suspend fun setTheme(value: ThemeMode) {
        context.settingsStore.edit { it[Keys.THEME] = value.name }
    }

    override suspend fun setAngle(value: AngleUnit) {
        context.settingsStore.edit { it[Keys.ANGLE] = value.name }
    }

    override suspend fun setDisplayMode(value: DisplayMode) {
        context.settingsStore.edit { it[Keys.DISPLAY_MODE] = value.name }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ANGLE = stringPreferencesKey("angle")
        val DISPLAY_MODE = stringPreferencesKey("displayMode")
    }
}