package com.qyf.rememberenglish.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 应用设置（CLAUDE.md：每日目标默认 20、提醒默认 20:00、深色模式） */
data class AppSettings(
    val dailyNewTarget: Int = SettingsRepository.DEFAULT_DAILY_TARGET,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = SettingsRepository.DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = SettingsRepository.DEFAULT_REMINDER_MINUTE,
    val lastNotifiedDay: String? = null,
    val darkMode: String = DarkMode.SYSTEM,
)

object DarkMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DAILY_NEW_TARGET = intPreferencesKey("daily_new_target")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_NOTIFIED_DAY = stringPreferencesKey("last_notified_day")
        val DARK_MODE = stringPreferencesKey("dark_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            dailyNewTarget = p[Keys.DAILY_NEW_TARGET] ?: DEFAULT_DAILY_TARGET,
            reminderEnabled = p[Keys.REMINDER_ENABLED] ?: true,
            reminderHour = p[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR,
            reminderMinute = p[Keys.REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE,
            lastNotifiedDay = p[Keys.LAST_NOTIFIED_DAY],
            darkMode = p[Keys.DARK_MODE] ?: DarkMode.SYSTEM,
        )
    }

    /** MainActivity 直接观察深色模式（null = 跟随系统） */
    val darkModeFlow: Flow<Boolean?> = context.dataStore.data.map { p ->
        when (p[Keys.DARK_MODE]) {
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
            else -> null
        }
    }

    suspend fun setDailyNewTarget(value: Int) {
        context.dataStore.edit { it[Keys.DAILY_NEW_TARGET] = value.coerceIn(5, 100) }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    /** 通知去重：记录最近一次已发送提醒的日期（yyyy-MM-dd） */
    suspend fun markNotifiedToday() {
        context.dataStore.edit { it[Keys.LAST_NOTIFIED_DAY] = LocalDate.now().toString() }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    companion object {
        const val DEFAULT_DAILY_TARGET = 20
        const val DEFAULT_REMINDER_HOUR = 20
        const val DEFAULT_REMINDER_MINUTE = 0
    }
}
