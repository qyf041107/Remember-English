package com.qyf.rememberenglish

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.qyf.rememberenglish.data.seed.DictSeeder
import com.qyf.rememberenglish.data.worker.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class RememberEnglishApp : Application(), Configuration.Provider {

    @Inject lateinit var seeder: DictSeeder
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // 词库预填充（幂等，首次启动导入红宝书）
        appScope.launch { seeder.seedIfNeeded() }
        // 应用启动时重排提醒（防 WorkManager 任务被系统清理后失效）
        appScope.launch { reminderScheduler.reschedule() }
    }

    /** 通知渠道：每日督促提醒（CLAUDE.md 第五节） */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_REMIND,
            getString(R.string.channel_remind_name),
            NotificationManager.IMPORTANCE_HIGH, // heads-up 弹窗，满足"弹窗督促"
        ).apply {
            description = getString(R.string.channel_remind_desc)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_REMIND = "remind_urgent"
    }
}
