package com.qyf.rememberenglish.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictWordDao(): DictWordDao
    abstract fun userWordDao(): UserWordDao
    abstract fun answerLogDao(): AnswerLogDao

    companion object {
        const val NAME = "remember_english.db"

        /**
         * v2 → v3（星标，用户 2026-09-16）：
         * ① user_word 加 isStarred——星标是词卡属性
         * ② answer_log 加 wasStarred——作答时快照，避免"日后打星让当天进度倒退"
         * `NOT NULL` 必须带 `DEFAULT`，否则表里已有数据时 ALTER 直接失败。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_word ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE answer_log ADD COLUMN wasStarred INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v1(SRS 表) → v2(分数模型)：App 未发布，直接销毁重建（v1 数据作废）。
         * 以方法引用传入，避免构建期误触发 fallback 逻辑。
         *
         * ⚠️ 不可改成无条件 fallbackToDestructiveMigration()——会清空用户全部背词记录。
         */
        fun build(context: android.content.Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
