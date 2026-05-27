package com.kevin.coupy.di

import android.content.Context
import androidx.room.Room
import com.kevin.coupy.data.AppDatabase
import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.dao.UsageEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 提供 AppDatabase 與 DAOs 給整個 App 注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DB_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCouponDao(database: AppDatabase): CouponDao = database.couponDao()

    @Provides
    fun provideUsageEventDao(database: AppDatabase): UsageEventDao = database.usageEventDao()
}
