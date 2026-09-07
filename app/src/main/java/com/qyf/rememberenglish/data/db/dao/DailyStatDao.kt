package com.qyf.rememberenglish.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qyf.rememberenglish.data.db.entity.DailyStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatDao {

    @Query("SELECT * FROM daily_stat WHERE day = :day")
    suspend fun get(day: String): DailyStatEntity?

    @Query("SELECT * FROM daily_stat WHERE day = :day")
    fun observe(day: String): Flow<DailyStatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStatEntity)

    /** 连续学习天数用：最近有记录的日子 */
    @Query("SELECT COUNT(*) FROM daily_stat WHERE newLearned + reviewsDone > 0")
    suspend fun countActiveDays(): Int
}
