package com.qyf.rememberenglish.domain.srs

import com.qyf.rememberenglish.domain.model.LearningState
import com.qyf.rememberenglish.domain.model.ReviewRating
import com.qyf.rememberenglish.domain.model.UserWord
import kotlin.math.ceil

/** SRS 全部常量集中于此（CLAUDE.md 第四节：禁止散落魔法数字） */
object SrsConstants {
    const val EASE_INIT = 2.5
    const val EASE_MIN = 1.3
    const val EASE_MAX = 3.0
    const val EASE_PENALTY_LAPSE = 0.2 // 复习态·不认识
    const val EASE_PENALTY_HARD = 0.15 // 复习态·模糊
    const val EASE_BONUS_GOOD = 0.1 // 复习态·认识
    const val HARD_INTERVAL_FACTOR = 1.2
    const val NEW_INTERVAL_FIRST = 1.0 // 新词首次"认识"
    const val NEW_INTERVAL_GRADUATE = 2.0 // 毕业进入复习态的间隔
    const val INTERVAL_MAX_DAYS = 365.0
    const val MASTERED_INTERVAL_DAYS = 21.0
    const val DAY_MILLIS = 86_400_000L
}

/**
 * 简化 SM-2 调度（CLAUDE.md 第五节公式）：
 * - 学习态：不认识→interval 0 当天重现｜模糊→1 天｜认识→1(streak=0)/2(streak≥1) 天，≥2 进复习态
 * - 复习态：不认识→ease−0.2 回学习态 1 天｜模糊→ease−0.15, ceil(int×1.2)｜认识→ease+0.1, ceil(int×ease)
 * - clamp：ease∈[1.3,3.0]、interval∈[0,365]；interval≥21 判定已掌握
 *
 * 纯函数：domain 层无 Android 依赖，可直接单测。
 */
object SrsScheduler {

    fun schedule(word: UserWord, rating: ReviewRating, now: Long): UserWord {
        return when (word.state) {
            LearningState.NEW, LearningState.LEARNING -> scheduleLearning(word, rating, now)
            LearningState.REVIEW -> scheduleReview(word, rating, now)
        }
    }

    private fun scheduleLearning(word: UserWord, rating: ReviewRating, now: Long): UserWord {
        val interval = when (rating) {
            // 当天内重现
            ReviewRating.AGAIN -> 0.0
            ReviewRating.HARD -> 1.0
            ReviewRating.GOOD -> if (word.streak == 0) SrsConstants.NEW_INTERVAL_FIRST else SrsConstants.NEW_INTERVAL_GRADUATE
        }
        val streak = if (rating == ReviewRating.AGAIN) 0 else word.streak + 1
        val graduate = rating == ReviewRating.GOOD && streak >= 2
        return word.copy(
            state = if (graduate) LearningState.REVIEW else LearningState.LEARNING,
            intervalDays = clampInterval(interval),
            streak = streak,
            reps = word.reps + 1,
            dueAt = now + (interval * SrsConstants.DAY_MILLIS).toLong(),
            isMastered = false,
        )
    }

    private fun scheduleReview(word: UserWord, rating: ReviewRating, now: Long): UserWord {
        val ease = when (rating) {
            ReviewRating.AGAIN -> word.ease - SrsConstants.EASE_PENALTY_LAPSE
            ReviewRating.HARD -> word.ease - SrsConstants.EASE_PENALTY_HARD
            ReviewRating.GOOD -> word.ease + SrsConstants.EASE_BONUS_GOOD
        }.coerceIn(SrsConstants.EASE_MIN, SrsConstants.EASE_MAX)

        val interval = when (rating) {
            // 回退到学习态，1 天后再见
            ReviewRating.AGAIN -> 1.0
            ReviewRating.HARD -> maxOf(2.0, ceil(word.intervalDays * SrsConstants.HARD_INTERVAL_FACTOR))
            ReviewRating.GOOD -> ceil(word.intervalDays * ease)
        }

        val backToLearning = rating == ReviewRating.AGAIN
        return word.copy(
            state = if (backToLearning) LearningState.LEARNING else LearningState.REVIEW,
            ease = ease,
            intervalDays = clampInterval(interval),
            reps = word.reps + 1,
            lapses = word.lapses + if (backToLearning) 1 else 0,
            streak = if (backToLearning) 0 else word.streak + 1,
            dueAt = now + (interval * SrsConstants.DAY_MILLIS).toLong(),
            isMastered = !backToLearning && interval >= SrsConstants.MASTERED_INTERVAL_DAYS,
        )
    }

    private fun clampInterval(days: Double): Double = days.coerceIn(0.0, SrsConstants.INTERVAL_MAX_DAYS)

    fun newWord(wordId: Long, now: Long): UserWord = UserWord(
        id = 0,
        wordId = wordId,
        addedAt = now,
        state = LearningState.NEW,
        ease = SrsConstants.EASE_INIT,
        intervalDays = 0.0,
        reps = 0,
        lapses = 0,
        streak = 0,
        dueAt = now,
        isSuspended = false,
        isMastered = false,
    )
}
