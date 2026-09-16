package com.qyf.rememberenglish.domain.model

/** 今日进度（CLAUDE.md：完成判定 = 当天背会的不同词数 ≥ 每日目标） */
data class DailyProgress(
    val day: String,
    /** 当天背会的不同词数（我知道/不清楚去重计数；星标词不计入） */
    val masteredToday: Int,
    val target: Int,
    /**
     * 今日目标是否在数学上可达（用户 2026-09-16 星标护栏）：
     * 星标词不计入今日背会，故当"我要背里未星标的词数 < 目标"时永远达不到目标，
     * 学习会话会无限继续。此标志供 StudyViewModel 提前收尾。
     * **不影响** [isDone]——否则词少的用户会被误判"今日已完成"而再也收不到提醒。
     */
    val reachable: Boolean = true,
) {
    val isDone: Boolean get() = masteredToday >= target
    val remaining: Int get() = (target - masteredToday).coerceAtLeast(0)
}
