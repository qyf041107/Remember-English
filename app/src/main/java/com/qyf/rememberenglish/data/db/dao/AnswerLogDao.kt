package com.qyf.rememberenglish.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.qyf.rememberenglish.data.db.entity.AnswerLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerLogDao {

    @Insert
    suspend fun insert(log: AnswerLogEntity)

    /** 当天背会的不同词数（我知道/不清楚计入，我不会不计；CLAUDE.md 完成判定） */
    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM answer_log
        WHERE reviewedAt >= :dayStartMillis AND rating != 0
        """,
    )
    suspend fun countMasteredSince(dayStartMillis: Long): Int

    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM answer_log
        WHERE reviewedAt >= :dayStartMillis AND rating != 0
        """,
    )
    fun observeMasteredSince(dayStartMillis: Long): Flow<Int>
}
