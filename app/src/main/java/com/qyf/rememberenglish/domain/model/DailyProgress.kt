package com.qyf.rememberenglish.domain.model

/** 今日进度（CLAUDE.md：完成判定 = 当天背会的不同词数 ≥ 每日目标） */
data class DailyProgress(
    val day: String,
    /** 当天背会的不同词数（我知道/不清楚去重计数） */
    val masteredToday: Int,
    val target: Int,
) {
    val isDone: Boolean get() = masteredToday >= target
    val remaining: Int get() = (target - masteredToday).coerceAtLeast(0)
}
