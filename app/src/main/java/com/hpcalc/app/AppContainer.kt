package com.hpcalc.app

import android.content.Context
import com.hpcalc.core.data.CalculatorDatabase
import com.hpcalc.core.data.HistoryDao
import com.hpcalc.core.data.SettingsRepository

/**
 * 手写依赖容器。
 *
 * 只有三个对象，引入 Hilt/Koin 带来的注解处理成本远大于收益，
 * 保持「一个 Application 持有一个容器」即可。
 */
class AppContainer(context: Context) {

    private val database: CalculatorDatabase = CalculatorDatabase.create(context)

    val historyDao: HistoryDao = database.historyDao()

    val settingsRepository: SettingsRepository = SettingsRepository(context)
}