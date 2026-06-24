package com.kevin.coupy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kevin.coupy.data.CouponStatus
import com.kevin.coupy.data.CouponType
import java.time.Instant
import java.time.LocalDate

/**
 * 票券資料表。
 *
 * Schema 跟 LINE bot 端 D1 對齊（worker/migrations/0001_initial.sql），
 * 之後若要做 LINE bot ↔ App 雙向同步可直接重用欄位名。
 *
 * v3 加：type（實體 / 數位）+ note（備註，最多 100 字）
 * v4 加：imagePath（票券照片本機路徑，出示給店員掃用）
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
    val usedAt: Instant? = null,

    /** v3 新增——實體 / 數位 */
    val type: CouponType = CouponType.PHYSICAL,

    /** v3 新增——備註（選填，UI 端限制 100 字） */
    val note: String? = null,

    /** v4 新增——票券照片本機檔案路徑（存在 coupon_images 私有目錄），null = 沒存照片 */
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null
)
