package com.qyf.rememberenglish.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.qyf.rememberenglish.data.db.dao.DailyStatDao
import com.qyf.rememberenglish.data.db.dao.DictWordDao
import com.qyf.rememberenglish.data.db.dao.ReviewLogDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.db.entity.DailyStatEntity
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.data.db.entity.ReviewLogEntity
import com.qyf.rememberenglish.data.db.entity.UserWordEntity

@Database(
    entities = [
        DictWordEntity::class,
        UserWordEntity::class,
        ReviewLogEntity::class,
        DailyStatEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictWordDao(): DictWordDao
    abstract fun userWordDao(): UserWordDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun dailyStatDao(): DailyStatDao

    companion object {
        const val NAME = "remember_english.db"
    }
}
