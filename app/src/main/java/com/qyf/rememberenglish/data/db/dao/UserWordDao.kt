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

    /** 我要背全量列表（用户 2026-09-16 指定：**新添加的在前**） */
    @Transaction
    @Query("SELECT * FROM user_word WHERE isSuspended = 0 ORDER BY addedAt DESC")
    fun observeAllWithWord(): Flow<List<UserWordWithWord>>

    @Query("SELECT wordId FROM user_word WHERE isSuspended = 0")
    fun observeAllWordIds(): Flow<List<Long>>

    /** 扫词页"已添加"标注：给一组单词，返回其中已在我要背的（用户 2026-09-10） */
    @Query(
        "SELECT dw.word FROM user_word uw JOIN dict_word dw ON dw.id = uw.wordId " +
            "WHERE uw.isSuspended = 0 AND dw.word IN (:words)",
    )
    suspend fun findMineWords(words: List<String>): List<String>

    /** 扫词页星标标注：给一组单词，返回其中已星标的（用户 2026-09-16） */
    @Query(
        "SELECT dw.word FROM user_word uw JOIN dict_word dw ON dw.id = uw.wordId " +
            "WHERE uw.isSuspended = 0 AND uw.isStarred = 1 AND dw.word IN (:words)",
    )
    suspend fun findStarredWords(words: List<String>): List<String>

    /** 我的页统计：全部与已掌握（满 5 分）词数 */
    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0")
    fun observeTotalCount(): Flow<Int>

    /** 星标词永不算已掌握（用户 2026-09-16），故这里排除 */
    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND score >= 5 AND isStarred = 0")
    fun observeMasteredCount(): Flow<Int>

    /** 未星标词数——今日目标可达性护栏用（星标词不计入今日背会） */
    @Query("SELECT COUNT(*) FROM user_word WHERE isSuspended = 0 AND isStarred = 0")
    fun observeUnstarredTotal(): Flow<Int>

    /** 词库/在线结果行的星标高亮 */
    @Query("SELECT wordId FROM user_word WHERE isSuspended = 0 AND isStarred = 1")
    fun observeStarredWordIds(): Flow<List<Long>>

    @Insert
    suspend fun insert(entity: UserWordEntity): Long

    @Update
    suspend fun update(entity: UserWordEntity)

    /** 只改星标位，避免整行写回 */
    @Query("UPDATE user_word SET isStarred = :starred WHERE wordId = :wordId")
    suspend fun setStarred(wordId: Long, starred: Boolean)

    @Query("DELETE FROM user_word WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
