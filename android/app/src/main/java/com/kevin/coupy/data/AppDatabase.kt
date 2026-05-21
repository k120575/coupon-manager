package com.kevin.coupy.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.entity.Converters
import com.kevin.coupy.data.entity.CouponEntity

/**
 * 券管家 Coupy 主資料庫。
 *
 * 目前不啟用 schema export（v1.0 還在快速迭代，schema 改動頻繁）。
 * 在第一次發行（上架 Play 商店）前要打開 exportSchema = true 並把
 * `schemas/` 加入 git，之後做 Room migration 才會有 baseline。
 */
@Database(
    entities = [CouponEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun couponDao(): CouponDao

    companion object {
        const val DB_NAME = "coupy.db"
    }
}
