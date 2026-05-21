package com.kevin.coupy.data

/**
 * 票券狀態。
 *
 * 資料庫欄位用 lowercase 字串儲存（跟 LINE bot 既有 schema 一致：'active' / 'used' / 'deleted'），
 * App 層用 enum 取得型別安全。
 */
enum class CouponStatus(val dbValue: String) {
    ACTIVE("active"),
    USED("used"),
    DELETED("deleted");

    companion object {
        fun fromDbValue(value: String): CouponStatus =
            entries.firstOrNull { it.dbValue == value } ?: ACTIVE
    }
}
