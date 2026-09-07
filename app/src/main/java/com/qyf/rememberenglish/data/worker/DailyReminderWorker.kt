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
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 每日提醒检查（CLAUDE.md 第五节）：
 * 到点检查当日完成度 —— 未完成才发 heads-up 通知；已完成或当天已提醒过则静默。
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

        val today = LocalDate.now().toString()
        // 当天已提醒过 → 不重复打扰
        if (settings.lastNotifiedDay == today) return Result.success()

        val progress = studyRepository.getTodayProgress()
        if (progress.isAllDone) return Result.success()

        val manager = NotificationManagerCompat.from(applicationContext)
        // 通知权限被关 → 静默跳过（"我的"页有开启引导）
        if (!manager.areNotificationsEnabled()) return Result.success()

        val remainingNew = progress.remainingNew
        val remainingReview = progress.remainingReviews
        val text = buildString {
            val parts = mutableListOf<String>()
            if (remainingNew > 0) parts.add("新词 $remainingNew 个")
            if (remainingReview > 0) parts.add("复习 $remainingReview 个")
            append(if (parts.isEmpty()) "今天的任务还没完成，加油！" else "还剩 ${parts.joinToString("、")}，坚持就是胜利！")
        }

        val notification = NotificationCompat.Builder(applicationContext, RememberEnglishApp.CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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
