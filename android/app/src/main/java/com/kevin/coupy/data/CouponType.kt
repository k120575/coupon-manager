package com.kevin.coupy.data

/**
 * 票券類型——區分「我要去找紙本」還是「打開某個 App」。
 *
 * 設計理由：使用者拿到券時第一個腦中閃過的問題就是「這張在哪？」——
 * 顯示這個欄位讓使用者列表上一眼就知道要去翻抽屜還是開 App。
 *
 * 預設 [PHYSICAL]——既有資料 migration 時也用這個值（手動建檔時期使用者多半是紙本記錄）。
 */
enum class CouponType(val dbValue: String) {
    PHYSICAL("physical"),
    DIGITAL("digital");

    /** UI 顯示用的繁中字 */
    val displayName: String
        get() = when (this) {
            PHYSICAL -> "實體"
            DIGITAL -> "數位"
        }

    companion object {
        fun fromDbValue(value: String): CouponType =
            entries.firstOrNull { it.dbValue == value } ?: PHYSICAL
    }
}
