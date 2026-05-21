package com.kevin.coupy.data

import java.time.LocalDate

/**
 * 票券相關的領域常數。
 */
object CouponConstants {

    /**
     * 「無期限」哨兵日期（9999-12-31）。
     *
     * 跟 LINE bot 後端 schema 對齊（worker/migrations/0001_initial.sql 註解）：
     * `expire_date TEXT NOT NULL, -- '9999-12-31' 代表無期限`
     *
     * 之所以用真實日期而不是 nullable：
     * - SQL 查詢更簡單（不用 NULL 特殊處理）
     * - 排序自然——無期限自動排在最後
     * - 跟 LINE bot 同 schema，未來雙向同步免轉換
     */
    val NO_EXPIRATION_DATE: LocalDate = LocalDate.of(9999, 12, 31)
}

fun LocalDate.isNoExpiration(): Boolean = this == CouponConstants.NO_EXPIRATION_DATE
