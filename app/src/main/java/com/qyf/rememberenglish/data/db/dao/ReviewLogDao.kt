package com.qyf.rememberenglish.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.qyf.rememberenglish.data.db.entity.ReviewLogEntity

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Insert
    suspend fun insertAll(logs: List<ReviewLogEntity>)

    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :since")
    suspend fun countSince(since: Long): Int
}
