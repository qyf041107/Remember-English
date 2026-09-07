package com.qyf.rememberenglish.data.repository

import com.qyf.rememberenglish.data.db.Mappers.toEntity
import com.qyf.rememberenglish.data.db.Mappers.toProgress
import com.qyf.rememberenglish.data.db.Mappers.toUserWord
import com.qyf.rememberenglish.data.db.dao.DailyStatDao
import com.qyf.rememberenglish.data.db.dao.ReviewLogDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.db.entity.DailyStatEntity
import com.qyf.rememberenglish.data.db.entity.ReviewLogEntity
import com.qyf.rememberenglish.data.settings.SettingsRepository
import com.qyf.rememberenglish.domain.model.DailyProgress
import com.qyf.rememberenglish.domain.model.LearningState
import com.qyf.rememberenglish.domain.model.QueueItem
import com.qyf.rememberenglish.domain.model.ReviewRating
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.srs.SrsScheduler
import com.qyf.rememberenglish.domain.select.WordSelector
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

@Singleton
class StudyRepository @Inject constructor(
    private val userWordDao: UserWordDao,
    private val reviewLogDao: ReviewLogDao,
    private val dailyStatDao: DailyStatDao,
    private val settingsRepository: SettingsRepository,
) {

    /** 今日 24:00（到期窗口边界） */
    fun endOfToday(now: LocalDate = LocalDate.now()): Long =
        now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun todayString(now: LocalDate = LocalDate.now()): String = now.toString()

    /**
     * 每日首次触达时快照"当日到期复习数"（CLAUDE.md 第五节），
     * 保证完成度不被当天清空到期列表稀释。幂等。
     */
    suspend fun ensureDailySnapshot(day: String = todayString()): DailyStatEntity {
        val existing = dailyStatDao.get(day)
        if (existing != null) return existing
        val snapshot = DailyStatEntity(
            day = day,
            newLearned = 0,
            reviewsDone = 0,
            reviewsDueAtDayStart = userWordDao.countDue(endOfToday()),
        )
        dailyStatDao.upsert(snapshot)
        return snapshot
    }

    /** 今日学习队列：到期复习（优先）+ 剩余目标内的新词 */
    suspend fun buildTodayQueue(): List<QueueItem> {
        val settings = settingsRepository.settingsFlow.first()
        ensureDailySnapshot()
        val stat = dailyStatDao.get(todayString())!!
        val due = userWordDao.getDue(endOfToday(), limit = 500).map { it.toUserWord() }
        val newWords = userWordDao.getNew(limit = settings.dailyNewTarget).map { it.toUserWord() }
        return WordSelector.buildQueue(
            dueReviews = due,
            newWords = newWords,
            remainingNewTarget = settings.dailyNewTarget - stat.newLearned,
        )
    }

    /**
     * 提交一次评分：SRS 调度 + 复习记录 + 每日统计（新词首评计入新词数，其余计入复习数）。
     * 返回调度后的词卡。
     */
    suspend fun submitReview(
        item: QueueItem,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis(),
    ): UserWord {
        val current = userWordDao.getById(item.userWord.id)!!.toUserWord()
        val isNewLearn = current.state == LearningState.NEW
        val scheduled = SrsScheduler.schedule(current, rating, now)

        userWordDao.update(scheduled.toEntity())
        reviewLogDao.insert(
            ReviewLogEntity(
                userWordId = current.id,
                wordId = current.wordId,
                rating = rating.code,
                reviewedAt = now,
                prevIntervalDays = current.intervalDays,
                newIntervalDays = scheduled.intervalDays,
            ),
        )
        bumpDailyStat(isNewLearn)
        return scheduled
    }

    private suspend fun bumpDailyStat(newLearn: Boolean) {
        val day = todayString()
        val stat = dailyStatDao.get(day) ?: ensureDailySnapshot(day)
        dailyStatDao.upsert(
            if (newLearn) stat.copy(newLearned = stat.newLearned + 1)
            else stat.copy(reviewsDone = stat.reviewsDone + 1),
        )
    }

    /** 今日进度观察（目标变化自动刷新） */
    fun observeTodayProgress(): Flow<DailyProgress> {
        val day = todayString()
        return combine(
            dailyStatDao.observe(day),
            settingsRepository.settingsFlow,
        ) { stat, settings ->
            (stat ?: ensureDailySnapshot(day)).toProgress(settings.dailyNewTarget)
        }
    }

    /** 当前完成度（M3 提醒判定复用：未完成才通知） */
    suspend fun getTodayProgress(): DailyProgress {
        val day = todayString()
        val stat = dailyStatDao.get(day) ?: ensureDailySnapshot(day)
        val settings = settingsRepository.settingsFlow.first()
        return stat.toProgress(settings.dailyNewTarget)
    }
}
