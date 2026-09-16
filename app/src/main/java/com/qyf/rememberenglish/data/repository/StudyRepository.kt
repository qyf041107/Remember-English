package com.qyf.rememberenglish.data.repository

import com.qyf.rememberenglish.data.db.Mappers.toEntity
import com.qyf.rememberenglish.data.db.Mappers.toUserWord
import com.qyf.rememberenglish.data.db.dao.AnswerLogDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.db.entity.AnswerLogEntity
import com.qyf.rememberenglish.data.settings.SettingsRepository
import com.qyf.rememberenglish.domain.model.AnswerRating
import com.qyf.rememberenglish.domain.model.DailyProgress
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.srs.ScoreScheduler
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
    private val answerLogDao: AnswerLogDao,
    private val settingsRepository: SettingsRepository,
) {

    fun todayStartMillis(now: LocalDate = LocalDate.now()): Long =
        now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun todayString(now: LocalDate = LocalDate.now()): String = now.toString()

    /**
     * 提交一次自评（CLAUDE.md 第五节分数模型）：
     * 我知道 +1 分｜不清楚 +0.5 分｜我不会 +0 分，写词卡分数并落一条作答记录。
     */
    suspend fun submitAnswer(
        userWord: UserWord,
        rating: AnswerRating,
        now: Long = System.currentTimeMillis(),
    ): UserWord {
        val current = userWordDao.getById(userWord.id)!!.toUserWord()
        val scheduled = ScoreScheduler.applyAnswer(current, rating, now)

        userWordDao.update(scheduled.toEntity())
        answerLogDao.insert(
            AnswerLogEntity(
                userWordId = current.id,
                wordId = current.wordId,
                rating = rating.code,
                reviewedAt = now,
                prevScore = current.score,
                newScore = scheduled.score,
                // 快照作答当时的星标状态（用户 2026-09-16）：星标词不计入今日背会
                wasStarred = current.isStarred,
            ),
        )
        return scheduled
    }

    /** 今日进度观察（完成判定：当天背会的不同词数 ≥ 目标） */
    fun observeTodayProgress(): Flow<DailyProgress> {
        val dayStart = todayStartMillis()
        return combine(
            answerLogDao.observeMasteredSince(dayStart),
            userWordDao.observeUnstarredTotal(),
            settingsRepository.settingsFlow,
        ) { mastered, unstarred, settings ->
            DailyProgress(
                day = todayString(),
                masteredToday = mastered,
                target = settings.dailyNewTarget,
                reachable = unstarred >= settings.dailyNewTarget,
            )
        }
    }

    /** 当前完成度（M3 提醒判定复用：未完成才通知） */
    suspend fun getTodayProgress(): DailyProgress {
        val settings = settingsRepository.settingsFlow.first()
        val mastered = answerLogDao.countMasteredSince(todayStartMillis())
        val unstarred = userWordDao.observeUnstarredTotal().first()
        return DailyProgress(
            day = todayString(),
            masteredToday = mastered,
            target = settings.dailyNewTarget,
            reachable = unstarred >= settings.dailyNewTarget,
        )
    }
}
