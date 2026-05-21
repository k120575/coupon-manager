package com.kevin.coupy.data.ocr

import java.time.LocalDate

/**
 * Gemini 從票券圖片解析出來的結構化資料。
 *
 * 所有欄位都可能 null（圖片不清楚 / Gemini 看不出來），UI 端要做 null-safe 處理。
 */
data class OcrResult(
    val name: String?,
    val expireDate: LocalDate?,
    val categoryId: String?,
    val quantity: Int?
) {
    val hasAnyData: Boolean
        get() = name != null || expireDate != null || categoryId != null || quantity != null
}
