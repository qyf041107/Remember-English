package com.qyf.rememberenglish.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictWordDao {

    /** 词库搜索：前缀命中优先，其余包含命中，再按字母序（简约搜索体验） */
    @Query(
        """
        SELECT * FROM dict_word
        WHERE source = 0 AND word LIKE '%' || :query || '%'
        ORDER BY CASE WHEN word LIKE :query || '%' THEN 0 ELSE 1 END, word
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 100): List<DictWordEntity>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<DictWordEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: DictWordEntity): Long
}
