package com.qyf.rememberenglish.data.di

import android.content.Context
import androidx.room.Room
import com.qyf.rememberenglish.data.db.AppDatabase
import com.qyf.rememberenglish.data.db.dao.DailyStatDao
import com.qyf.rememberenglish.data.db.dao.DictWordDao
import com.qyf.rememberenglish.data.db.dao.ReviewLogDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideDictWordDao(db: AppDatabase): DictWordDao = db.dictWordDao()

    @Provides
    fun provideUserWordDao(db: AppDatabase): UserWordDao = db.userWordDao()

    @Provides
    fun provideReviewLogDao(db: AppDatabase): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideDailyStatDao(db: AppDatabase): DailyStatDao = db.dailyStatDao()

    /** 应用级协程作用域：词库预填充等长任务 */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
