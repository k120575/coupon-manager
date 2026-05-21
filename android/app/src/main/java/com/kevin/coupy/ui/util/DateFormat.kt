package com.kevin.coupy.ui.util

import com.kevin.coupy.data.isNoExpiration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/**
 * 格式化到期日為相對描述或絕對日期。
 *
 * 輸出：
 *   - 無期限 (9999-12-31) → "永久有效"
 *   - daysUntil = -1  → "已過期"
 *   - daysUntil = 0   → "今天到期"
 *   - daysUntil = 1   → "明天到期"
 *   - daysUntil = 3   → "3 天後到期"
 *   - daysUntil = 100 → "2026/08/29"
 */
fun formatExpireDate(date: LocalDate, daysUntil: Long): String = when {
    date.isNoExpiration() -> "永久有效"
    daysUntil < 0 -> "已過期"
    daysUntil == 0L -> "今天到期"
    daysUntil == 1L -> "明天到期"
    daysUntil <= 7 -> "${daysUntil} 天後到期"
    else -> date.format(DateFormatter)
}
