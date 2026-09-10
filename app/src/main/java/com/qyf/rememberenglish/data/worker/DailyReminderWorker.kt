package com.qyf.rememberenglish.data.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qyf.rememberenglish.MainActivity
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.RememberEnglishApp
import com.qyf.rememberenglish.data.repository.StudyRepository
import com.qyf.rememberenglish.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 每日提醒检查（CLAUDE.md 第五节）：
 * 到点检查当日完成度 —— 背会词数未达标才发 heads-up 通知；已完成或当天已提醒过则静默。
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.reminderEnabled) return Result.success()

        val today = studyRepository.todayString()
        // 当天已提醒过 → 不重复打扰
        if (settings.lastNotifiedDay == today) return Result.success()

        val progress = studyRepository.getTodayProgress()
        if (progress.isDone) return Result.success()

        val manager = NotificationManagerCompat.from(applicationContext)
        // 通知权限被关 → 静默跳过（"我的"页有开启引导）
        if (!manager.areNotificationsEnabled()) return Result.success()

        val text = applicationContext.getString(
            R.string.notification_body,
            progress.masteredToday,
            progress.target,
            progress.remaining,
        )

        val notification = NotificationCompat.Builder(applicationContext, RememberEnglishApp.CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            // 进度条 +「去背单词」快捷按钮（用户 2026-09-10，灵动岛替代方案）
            .setProgress(progress.target, progress.masteredToday, false)
            .addAction(0, applicationContext.getString(R.string.notification_action_study), deepLinkIntent())
            .setContentIntent(deepLinkIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 未授予时的兜底
            return Result.success()
        }
        settingsRepository.markNotifiedToday()
        return Result.success()
    }

    /** 点击通知直达今日学习页 */
    private fun deepLinkIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(Intent.ACTION_VIEW, Uri.parse("rememberenglish://study")).setClass(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
