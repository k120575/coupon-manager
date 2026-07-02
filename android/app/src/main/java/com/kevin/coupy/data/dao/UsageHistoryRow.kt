package com.kevin.coupy.data.dao

import java.time.Instant

/**
 * 使用紀錄頁的查詢回傳型別（usage_events JOIN coupons 取名稱）。
 *
 * couponName 取自 coupons 目前的名稱；即使票券已用完（status='used'）或
 * 軟刪除（status='deleted'），JOIN 仍成立——使用歷史不因票券退場而消失。
 */
data class UsageHistoryRow(
    val usedAt: Instant,
    val couponName: String,
    val count: Int
)
