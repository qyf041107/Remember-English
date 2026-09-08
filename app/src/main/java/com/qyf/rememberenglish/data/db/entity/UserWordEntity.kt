package com.qyf.rememberenglish.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** "我要背"词卡 + 分数状态（CLAUDE.md 第五节分数模型） */
@Entity(
    tableName = "user_word",
    indices = [Index(value = ["wordId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = DictWordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class UserWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val addedAt: Long,
    /** 背会分数：我知道 +1、不清楚 +0.5、我不会 +0；≥5 已掌握 */
    val score: Double = 0.0,
    /** 点过「我不会」的次数 */
    val wrongCount: Int = 0,
    /** 点过「不清楚」的次数 */
    val unclearCount: Int = 0,
    /** 最近一次作答时间；0 = 从未作答 */
    val lastAnsweredAt: Long = 0L,
    val isSuspended: Boolean = false,
)
