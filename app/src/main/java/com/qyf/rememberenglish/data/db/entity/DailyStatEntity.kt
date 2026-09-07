package com.qyf.rememberenglish.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 每日统计与完成度（CLAUDE.md：完成判定 = newLearned≥目标 AND reviewsDone≥当日到期快照） */
@Entity(tableName = "daily_stat")
data class DailyStatEntity(
    /** 本地日期，格式 yyyy-MM-dd */
    @PrimaryKey val day: String,
    val newLearned: Int = 0,
    val reviewsDone: Int = 0,
    /** 当日首次启动时快照的到期复习数，保证完成度不被"清空到期列表"稀释 */
    val reviewsDueAtDayStart: Int = 0,
)
