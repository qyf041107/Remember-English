package com.qyf.rememberenglish.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 复习记录（M2 学习会话落库） */
@Entity(
    tableName = "review_log",
    indices = [Index(value = ["userWordId"]), Index(value = ["reviewedAt"])],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userWordId: Long,
    val wordId: Long,
    /** 0 = 不认识；1 = 模糊；2 = 认识 */
    val rating: Int,
    val reviewedAt: Long,
    val prevIntervalDays: Double,
    val newIntervalDays: Double,
)
