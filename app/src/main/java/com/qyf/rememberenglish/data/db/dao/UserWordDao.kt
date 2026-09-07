package com.qyf.rememberenglish.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.data.db.entity.UserWordEntity
import kotlinx.coroutines.flow.Flow

/** 词卡 + 词条联查 POJO（我要背列表用） */
data class UserWordWithWord(
    @Embedded val userWord: UserWordEntity,
    @Relation(parentColumn = "wordId", entityColumn = "id") val word: DictWordEntity,
)

@Dao
interface UserWordDao {

    @Query("SELECT * FROM user_word WHERE wordId = :wordId LIMIT 1")
    suspend fun findByWordId(wordId: Long): UserWordEntity?

    @Query("SELECT * FROM user_word WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserWordEntity?

    /** 我要背全量列表（新词在前按加入顺序） */
    @Query("SELECT * FROM user_word WHERE isSuspended = 0 ORDER BY state ASC, addedAt ASC")
    fun observeAll(): Flow<List<UserWordEntity>>

    @Transaction
    @Query("SELECT * FROM user_word WHERE isSuspended = 0 ORDER BY state ASC, addedAt ASC")
    fun observeAllWithWord(): Flow<List<UserWordWithWord>>

    /** 到期复习：state != 0（新词由选词逻辑单独取），dueAt ≤ 今日结束 */
    @Query(
        """
        SELECT * FROM user_word
        WHERE isSuspended = 0 AND state != 0 AND dueAt <= :endOfToday
        ORDER BY dueAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getDue(endOfToday: Long, limit: Int): List<UserWordEntity>

    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND state != 0 AND dueAt <= :endOfToday")
    suspend fun countDue(endOfToday: Long): Int

    /** 新词队列：按加入顺序（FIFO，先加先学） */
    @Query(
        """
        SELECT * FROM user_word
        WHERE isSuspended = 0 AND state = 0
        ORDER BY addedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getNew(limit: Int): List<UserWordEntity>

    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND state = 0")
    fun observeNewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND state = 1")
    fun observeLearningCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND isMastered = 1")
    fun observeMasteredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT wordId FROM user_word WHERE isSuspended = 0")
    fun observeAllWordIds(): Flow<List<Long>>

    @Insert
    suspend fun insert(entity: UserWordEntity): Long

    @Update
    suspend fun update(entity: UserWordEntity)

    @Query("DELETE FROM user_word WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
