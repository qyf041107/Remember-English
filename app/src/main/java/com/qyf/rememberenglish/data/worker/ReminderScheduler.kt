package com.qyf.rememberenglish.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qyf.rememberenglish.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 提醒调度（CLAUDE.md 第五节）：WorkManager 24h 周期任务，初始延迟对齐到下一次提醒时刻。
 * 用 UPDATE 策略保证用户改时间/开关后立即生效；不使用精确闹钟，无需特殊权限。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun reschedule() {
        val settings = settingsRepository.settingsFlow.first()
        val workManager = WorkManager.getInstance(context)
        if (!settings.reminderEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val target = LocalTime.of(settings.reminderHour, settings.reminderMinute)
        val now = LocalDateTime.now()
        val nextRun = if (now.toLocalTime() < target) now.with(target) else now.plusDays(1).with(target)
        val initialDelay = Duration.between(now, nextRun).coerceAtLeast(Duration.ZERO)

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "daily_reminder"
        private const val TAG = "reminder"
    }
}
