package com.example.calculator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.calculator.domain.AngleUnit
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
data class Settings(val dark:Boolean=false,val language:String="zh",val angle:AngleUnit=AngleUnit.RADIANS)
class SettingsRepository(private val context:Context){
    val settings=context.settingsStore.data.map{p->Settings(p[Keys.DARK]?:false,p[Keys.LANGUAGE]?:"zh",runCatching{AngleUnit.valueOf(p[Keys.ANGLE]?:"RADIANS")}.getOrDefault(AngleUnit.RADIANS))}
    suspend fun setDark(v:Boolean)=context.settingsStore.edit{it[Keys.DARK]=v}
    suspend fun setLanguage(v:String)=context.settingsStore.edit{it[Keys.LANGUAGE]=v}
    suspend fun setAngle(v:AngleUnit)=context.settingsStore.edit{it[Keys.ANGLE]=v.name}
    private object Keys{val DARK=booleanPreferencesKey("dark");val LANGUAGE=stringPreferencesKey("language");val ANGLE=stringPreferencesKey("angle")}
}
