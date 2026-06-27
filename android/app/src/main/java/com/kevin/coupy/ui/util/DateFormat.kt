package com.kevin.coupy.ui.util

import com.kevin.coupy.data.isNoExpiration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val ShortDateFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 格式化到期日為相對描述或絕對日期。
 *
 * 輸出：
 *   - 無期限 (9999-12-31) → "永久有效"
 *   - daysUntil = -1  → "已過期 1 天 · 5/29"（顯示過期多久 + 原到期日，讓使用者分辨剛過期還是早就死）
 *   - daysUntil = -30 → "已過期 30 天 · 4/30"
 *   - daysUntil = 0   → "今天到期"
 *   - daysUntil = 1   → "明天到期"
 *   - daysUntil = 3   → "還有 3 天"
 *   - daysUntil = 100 → "2026/08/29"
 */
fun formatExpireDate(date: LocalDate, daysUntil: Long): String = when {
    date.isNoExpiration() -> "永久有效"
    daysUntil < 0 -> "已過期 ${-daysUntil} 天 · ${date.format(ShortDateFormatter)}"
    daysUntil == 0L -> "今天到期"
    daysUntil == 1L -> "明天到期"
    daysUntil <= 7 -> "還有 ${daysUntil} 天"
    else -> date.format(DateFormatter)
}
