package com.qyf.rememberenglish.domain.model

/** 今日进度（CLAUDE.md：完成判定 = 新词达标 且 复习达快照数） */
data class DailyProgress(
    val day: String,
    val newLearned: Int,
    val newTarget: Int,
    val reviewsDone: Int,
    val reviewsDue: Int,
) {
    val isNewDone: Boolean get() = newLearned >= newTarget
    val isReviewDone: Boolean get() = reviewsDone >= reviewsDue
    val isAllDone: Boolean get() = isNewDone && isReviewDone
    val remainingNew: Int get() = (newTarget - newLearned).coerceAtLeast(0)
    val remainingReviews: Int get() = (reviewsDue - reviewsDone).coerceAtLeast(0)
}
