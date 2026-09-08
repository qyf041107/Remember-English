package com.qyf.rememberenglish.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import kotlinx.coroutines.flow.Flow

/** 轻量词头（模糊搜索排序用，避免整表带释义加载） */
data class WordHead(
    val id: Long,
    val word: String,
)

@Dao
interface DictWordDao {

    /** 搜索排序用的轻量词头（词库词 source=0 + 词组 source=2，不含自定义词） */
    @Query("SELECT id, word FROM dict_word WHERE source IN (0, 2)")
    suspend fun getAllHeads(): List<WordHead>

    @Query("SELECT * FROM dict_word WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DictWordEntity>

    @Query("SELECT * FROM dict_word WHERE word = :word COLLATE NOCASE LIMIT 1")
    suspend fun findByWord(word: String): DictWordEntity?

    @Query("SELECT * FROM dict_word WHERE word IN (:words)")
    suspend fun findByWords(words: List<String>): List<DictWordEntity>

    @Query("SELECT * FROM dict_word WHERE id = :id")
    fun observeById(id: Long): Flow<DictWordEntity?>

    @Query("SELECT * FROM dict_word WHERE id = :id")
    suspend fun getById(id: Long): DictWordEntity?

    @Query("SELECT COUNT(*) FROM dict_word")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM dict_word WHERE source = :source")
    suspend fun countBySource(source: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<DictWordEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: DictWordEntity): Long
}
