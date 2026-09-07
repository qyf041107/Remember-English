package com.qyf.rememberenglish.domain.model

/** "我要背"词卡领域模型（SRS 状态由 [com.qyf.rememberenglish.domain.srs.SrsScheduler] 维护） */
data class UserWord(
    val id: Long,
    val wordId: Long,
    val addedAt: Long,
    val state: LearningState,
    val ease: Double,
    val intervalDays: Double,
    val reps: Int,
    val lapses: Int,
    val streak: Int,
    val dueAt: Long,
    val isSuspended: Boolean,
    val isMastered: Boolean,
)
