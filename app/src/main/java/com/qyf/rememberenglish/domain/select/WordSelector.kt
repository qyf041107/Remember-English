package com.qyf.rememberenglish.domain.select

import com.qyf.rememberenglish.domain.model.LearningState
import com.qyf.rememberenglish.domain.model.QueueItem
import com.qyf.rememberenglish.domain.model.UserWord

/**
 * 每日选词（CLAUDE.md 第五节）：到期复习优先（dueAt 升序），其后新词按加入顺序补足剩余目标。
 * 纯函数，可单测。
 */
object WordSelector {

    fun buildQueue(
        dueReviews: List<UserWord>,
        newWords: List<UserWord>,
        remainingNewTarget: Int,
    ): List<QueueItem> {
        val reviews = dueReviews
            .filter { !it.isSuspended }
            .sortedBy { it.dueAt }
            .map { QueueItem(it, isNew = false) }
        val newLimit = remainingNewTarget.coerceAtLeast(0)
        val news = newWords
            .filter { !it.isSuspended && it.state == LearningState.NEW }
            .sortedBy { it.addedAt }
            .take(newLimit)
            .map { QueueItem(it, isNew = true) }
        return reviews + news
    }
}
