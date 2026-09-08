package com.qyf.rememberenglish.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 自评作答记录（每日完成度按它去重统计） */
@Entity(
    tableName = "answer_log",
    indices = [Index(value = ["reviewedAt"]), Index(value = ["wordId"])],
)
data class AnswerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userWordId: Long,
    val wordId: Long,
    /** 0 = 我不会；1 = 不清楚；2 = 我知道（AnswerRating.code） */
    val rating: Int,
    val reviewedAt: Long,
    val prevScore: Double,
    val newScore: Double,
)
