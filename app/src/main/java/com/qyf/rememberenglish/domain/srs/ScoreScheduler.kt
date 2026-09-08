package com.qyf.rememberenglish.domain.srs

import com.qyf.rememberenglish.domain.model.AnswerRating
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.srs.ScoreConstants.MASTER_SCORE
import com.qyf.rememberenglish.domain.srs.ScoreConstants.MASTERED_PICK_WEIGHT
import com.qyf.rememberenglish.domain.srs.ScoreConstants.SCORE_INIT
import com.qyf.rememberenglish.domain.srs.ScoreConstants.SCORE_KNOWN
import com.qyf.rememberenglish.domain.srs.ScoreConstants.SCORE_UNCLEAR
import com.qyf.rememberenglish.domain.srs.ScoreConstants.SCORE_WRONG

/** 分数模型全部常量集中于此（CLAUDE.md 第四节：禁止散落魔法数字） */
object ScoreConstants {
    const val SCORE_KNOWN = 1.0 // 我知道
    const val SCORE_UNCLEAR = 0.5 // 不清楚
    const val SCORE_WRONG = 0.0 // 我不会
    const val MASTER_SCORE = 5.0 // 满 5 分 = 已掌握
    /** 复习阶段：已掌握词出现率打 0.25 折（用户 2026-09-07：权重 0.25，一笔带过） */
    const val MASTERED_PICK_WEIGHT = 0.25
    /** 新词加入时的初始分数 */
    const val SCORE_INIT = 0.0
}

/**
 * 分数调度（CLAUDE.md 第五节，用户 2026-09-07 定义）：
 * 我知道 +1 分｜不清楚 +0.5 分｜我不会 +0 分；满 5 分即已掌握。
 * 纯函数：domain 层无 Android 依赖，可直接单测。
 */
object ScoreScheduler {

    fun applyAnswer(word: UserWord, rating: AnswerRating, now: Long): UserWord {
        val gain = when (rating) {
            AnswerRating.WRONG -> SCORE_WRONG
            AnswerRating.UNCLEAR -> SCORE_UNCLEAR
            AnswerRating.KNOW -> SCORE_KNOWN
        }
        return word.copy(
            score = word.score + gain,
            wrongCount = word.wrongCount + if (rating == AnswerRating.WRONG) 1 else 0,
            unclearCount = word.unclearCount + if (rating == AnswerRating.UNCLEAR) 1 else 0,
            lastAnsweredAt = now,
        )
    }

    fun newWord(wordId: Long, now: Long): UserWord = UserWord(
        id = 0,
        wordId = wordId,
        addedAt = now,
        score = SCORE_INIT,
        wrongCount = 0,
        unclearCount = 0,
        lastAnsweredAt = 0L,
        isSuspended = false,
    )

    /** 提问权重：不会/不清楚过的词优先，已掌握词打 0.25 折（复习着重错词，已掌握一笔带过） */
    fun pickWeight(word: UserWord): Double {
        val base = 1.0 + word.wrongCount + word.unclearCount
        return if (word.isMastered) base * MASTERED_PICK_WEIGHT else base
    }

    /** 满 [MASTER_SCORE] 分判定已掌握 */
    fun isMastered(score: Double): Boolean = score >= MASTER_SCORE
}
