package com.qyf.rememberenglish.domain.model

import com.qyf.rememberenglish.domain.srs.ScoreConstants

/** "我要背"词卡领域模型（分数由 [com.qyf.rememberenglish.domain.srs.ScoreScheduler] 维护） */
data class UserWord(
    val id: Long,
    val wordId: Long,
    val addedAt: Long,
    /** 背会分数：我知道 +1、不清楚 +0.5、我不会 +0（CLAUDE.md 第五节） */
    val score: Double,
    /** 点过「我不会」的次数（复习加权用） */
    val wrongCount: Int,
    /** 点过「不清楚」的次数（复习加权用） */
    val unclearCount: Int,
    /** 最近一次作答时间；0 = 从未作答 */
    val lastAnsweredAt: Long,
    val isSuspended: Boolean,
) {
    /** 满 [ScoreConstants.MASTER_SCORE] 分即已掌握 */
    val isMastered: Boolean get() = score >= ScoreConstants.MASTER_SCORE

    /** 0 分且从未作答 = 只添加过还没背 */
    val isNew: Boolean get() = score <= 0.0 && lastAnsweredAt == 0L
}
