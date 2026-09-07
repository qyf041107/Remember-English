package com.qyf.rememberenglish.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 词条（词库表）。自定义词也进此表，用 [source] 区分（CLAUDE.md 第五节），
 * 使内置词与 OCR/手动添加的自定义词在数据模型上完全同构。
 */
@Entity(
    tableName = "dict_word",
    indices = [Index(value = ["word"], unique = true)],
)
data class DictWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val usphone: String,
    val ukphone: String,
    /** JSON 数组字符串，如 ["n. 苹果","v. 给……定价"] */
    val meanings: String,
    /** 0 = 内置红宝书；1 = 用户自定义（OCR/手动） */
    val source: Int,
    val createdAt: Long,
) {
    companion object {
        const val SOURCE_BUILTIN = 0
        const val SOURCE_CUSTOM = 1
    }
}
