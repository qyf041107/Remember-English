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

    /** 我要背全量列表（新添加在前） */
    @Transaction
    @Query("SELECT * FROM user_word WHERE isSuspended = 0 ORDER BY addedAt ASC")
    fun observeAllWithWord(): Flow<List<UserWordWithWord>>

    @Query("SELECT wordId FROM user_word WHERE isSuspended = 0")
    fun observeAllWordIds(): Flow<List<Long>>

    /** 我的页统计：全部与已掌握（满 5 分）词数 */
    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND score >= 5")
    fun observeMasteredCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: UserWordEntity): Long

    @Update
    suspend fun update(entity: UserWordEntity)

    @Query("DELETE FROM user_word WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
