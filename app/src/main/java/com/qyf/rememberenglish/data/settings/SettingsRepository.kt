package com.qyf.rememberenglish.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** 云端手写识别（百度，用户 2026-09-08 要求）：密钥用户自填，只存本地 */
    val cloudOcrEnabled: Boolean = false,
    val baiduApiKey: String = "",
    val baiduSecretKey: String = "",
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
        val CLOUD_OCR_ENABLED = booleanPreferencesKey("cloud_ocr_enabled")
        val BAIDU_API_KEY = stringPreferencesKey("baidu_api_key")
        val BAIDU_SECRET_KEY = stringPreferencesKey("baidu_secret_key")

        // 扫词页忽略词（用户 2026-09-10：date/sun 等 OCR 伪词每次都出现，忽略后不再打扰）
        val OCR_IGNORED_WORDS = stringSetPreferencesKey("ocr_ignored_words")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            dailyNewTarget = p[Keys.DAILY_NEW_TARGET] ?: DEFAULT_DAILY_TARGET,
            reminderEnabled = p[Keys.REMINDER_ENABLED] ?: true,
            reminderHour = p[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR,
            reminderMinute = p[Keys.REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE,
            lastNotifiedDay = p[Keys.LAST_NOTIFIED_DAY],
            darkMode = p[Keys.DARK_MODE] ?: DarkMode.SYSTEM,
            cloudOcrEnabled = p[Keys.CLOUD_OCR_ENABLED] ?: false,
            baiduApiKey = p[Keys.BAIDU_API_KEY] ?: "",
            baiduSecretKey = p[Keys.BAIDU_SECRET_KEY] ?: "",
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

    /** 扫词页忽略词集合（持久化；忽略的伪词不再出现在候选列表） */
    val ocrIgnoredWordsFlow: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[Keys.OCR_IGNORED_WORDS] ?: emptySet()
    }

    suspend fun addOcrIgnoredWord(word: String) {
        context.dataStore.edit { it[Keys.OCR_IGNORED_WORDS] = (it[Keys.OCR_IGNORED_WORDS] ?: emptySet()) + word }
    }

    suspend fun removeOcrIgnoredWord(word: String) {
        context.dataStore.edit { it[Keys.OCR_IGNORED_WORDS] = (it[Keys.OCR_IGNORED_WORDS] ?: emptySet()) - word }
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

    suspend fun setCloudOcrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_OCR_ENABLED] = enabled }
    }

    suspend fun setBaiduKeys(apiKey: String, secretKey: String) {
        context.dataStore.edit {
            it[Keys.BAIDU_API_KEY] = apiKey.trim()
            it[Keys.BAIDU_SECRET_KEY] = secretKey.trim()
        }
    }

    companion object {
        const val DEFAULT_DAILY_TARGET = 20
        const val DEFAULT_REMINDER_HOUR = 20
        const val DEFAULT_REMINDER_MINUTE = 0
    }
}
