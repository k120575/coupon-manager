package com.kevin.coupy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * 使用事件：每次 [com.kevin.coupy.data.CouponRepository.use] 都會插一筆。
 *
 * 為什麼要這張表：原本只能從 [CouponEntity.status]='used' + [CouponEntity.usedAt] 推算用完時間，
 * 但分次使用（用 2/5 張）完全沒紀錄。要正確統計「本月使用 N 張」必須有 per-event timeline。
 *
 * Migration v1→v2 會從現有 used 票券補一筆事件（couponId, quantity, usedAt），保留歷史。
 */
@Entity(tableName = "usage_events")
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "coupon_id")
    val couponId: Long,

    val count: Int,

    @ColumnInfo(name = "used_at")
    val usedAt: Instant
)
