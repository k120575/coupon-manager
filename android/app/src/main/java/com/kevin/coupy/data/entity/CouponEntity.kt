package com.kevin.coupy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kevin.coupy.data.CouponStatus
import java.time.Instant
import java.time.LocalDate

/**
 * 票券資料表。
 *
 * Schema 跟 LINE bot 端 D1 對齊（worker/migrations/0001_initial.sql），
 * 之後若要做 LINE bot ↔ App 雙向同步可直接重用欄位名。
 */
@Entity(tableName = "coupons")
data class CouponEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "expire_date")
    val expireDate: LocalDate,

    val category: String,

    val quantity: Int = 1,

    val status: CouponStatus = CouponStatus.ACTIVE,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "used_at")
    val usedAt: Instant? = null
)
