package com.qyf.rememberenglish.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** "我要背"词卡 + SRS 调度状态（CLAUDE.md 第五节） */
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
    /** 0 = 新词（未学）；1 = 学习中；2 = 复习阶段 */
    val state: Int,
    /** SM-2 ease 因子，clamp [1.3, 3.0] */
    val ease: Double = 2.5,
    /** 当前间隔天数；0 表示当天内重现 */
    val intervalDays: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** 连续答对次数（rating != 不认识 计为答对） */
    val streak: Int = 0,
    /** 下次到期时间（epoch millis）；interval=0 时为当前时刻，会话内重现 */
    val dueAt: Long,
    val isSuspended: Boolean = false,
    /** interval ≥ 21 天判定为已掌握（SrsScheduler 维护） */
    val isMastered: Boolean = false,
)
