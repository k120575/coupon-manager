package com.kevin.coupy.data.backup

/**
 * JSON 備份檔的版本號。
 *
 * 之後若 schema 變動，舊版本仍可被新版 App 讀取（透過遷移邏輯），
 * 但新版備份不保證舊版 App 可讀。
 */
const val BACKUP_FORMAT_VERSION = 1

/** 匯入結果：給 UI 顯示「新增 N 筆，跳過 M 筆」之類訊息用 */
data class ImportResult(
    val imported: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int
) {
    val total: Int get() = imported + skippedDuplicates + skippedInvalid
}
