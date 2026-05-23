package com.kevin.coupy.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.dao.UsageEventDao
import com.kevin.coupy.data.entity.Converters
import com.kevin.coupy.data.entity.CouponEntity
import com.kevin.coupy.data.entity.UsageEventEntity

/**
 * 券管家 Coupy 主資料庫。
 *
 * 目前不啟用 schema export（v1.0 還在快速迭代，schema 改動頻繁）。
 * 在第一次發行（上架 Play 商店）前要打開 exportSchema = true 並把
 * `schemas/` 加入 git，之後做 Room migration 才會有 baseline。
 */
@Database(
    entities = [CouponEntity::class, UsageEventEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun couponDao(): CouponDao
    abstract fun usageEventDao(): UsageEventDao

    companion object {
        const val DB_NAME = "coupy.db"

        /**
         * v1 → v2：新增 usage_events 表追蹤每次使用事件。
         *
         * 從現有 status='used' 的 coupon 補一筆事件（用 final quantity + usedAt），
         * 保留歷史用量數據；分次使用無紀錄的部分無法回填，是已知缺陷。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `usage_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `coupon_id` INTEGER NOT NULL,
                        `count` INTEGER NOT NULL,
                        `used_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO usage_events (coupon_id, count, used_at)
                    SELECT id, quantity, used_at FROM coupons
                    WHERE status = 'used' AND used_at IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        /**
         * v2 → v3：coupons 加 type（實體/數位）+ note 欄位。
         *
         * 既有資料 type 預設 'physical'（多數歷史資料是手動建檔的紙本券）；
         * note 留 NULL（沒備註資料可遷移）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coupons ADD COLUMN type TEXT NOT NULL DEFAULT 'physical'")
                db.execSQL("ALTER TABLE coupons ADD COLUMN note TEXT")
            }
        }
    }
}
