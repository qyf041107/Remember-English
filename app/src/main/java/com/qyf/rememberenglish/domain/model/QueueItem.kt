package com.qyf.rememberenglish.domain.model

/** 学习队列条目 */
data class QueueItem(
    val userWord: UserWord,
    val isNew: Boolean,
)
