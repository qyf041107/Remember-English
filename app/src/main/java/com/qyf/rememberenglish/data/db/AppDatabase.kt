package com.qyf.rememberenglish.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import com.qyf.rememberenglish.data.db.dao.AnswerLogDao
import com.qyf.rememberenglish.data.db.dao.DictWordDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.db.entity.AnswerLogEntity
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.data.db.entity.UserWordEntity

@Database(
    entities = [
        DictWordEntity::class,
        UserWordEntity::class,
        AnswerLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictWordDao(): DictWordDao
    abstract fun userWordDao(): UserWordDao
    abstract fun answerLogDao(): AnswerLogDao

    companion object {
        const val NAME = "remember_english.db"

        /**
         * v1(SRS 表) → v2(分数模型)：App 未发布，直接销毁重建（v1 数据作废）。
         * 以方法引用传入，避免构建期误触发 fallback 逻辑。
         */
        fun build(context: android.content.Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
