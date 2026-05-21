package com.kevin.coupy.data.entity

import androidx.room.TypeConverter
import com.kevin.coupy.data.CouponStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Room TypeConverter：把 LocalDate / Instant / CouponStatus 對應到 SQLite 原生型別。
 *
 * - LocalDate ↔ String（ISO 8601: "2026-12-31"），方便閱讀 + 直接排序
 * - Instant ↔ Long（epoch millis），最小空間最快查詢
 * - CouponStatus ↔ String，跟 LINE bot D1 schema 對齊
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromCouponStatus(status: CouponStatus): String = status.dbValue

    @TypeConverter
    fun toCouponStatus(value: String): CouponStatus = CouponStatus.fromDbValue(value)
}
