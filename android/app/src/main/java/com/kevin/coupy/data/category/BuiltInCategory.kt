package com.kevin.coupy.data.category

/**
 * 內建 14 個分類。
 *
 * 設計原則：
 * - **id 是 DB 主鍵**（票券表的 `category` 欄位儲存這個值），永遠不變
 * - **defaultName 是預設顯示名**，使用者可在設定裡重新命名（透過 CategoryRepository）
 * - **emoji 是 v1.0 的圖示**，Pro 版可自訂顏色/圖示（v1.1+）
 *
 * id 一旦發布就不能改——改了會讓使用者既有的票券分類消失。
 * 要新增分類請加在 OTHER 之前，並且 Pro 版才能讓使用者「真正新增自訂分類」。
 */
enum class BuiltInCategory(
    val id: String,
    val defaultName: String,
    val emoji: String
) {
    DINING("dining", "餐飲", "🍱"),
    MOVIE("movie", "電影", "🎬"),
    SHOPPING("shopping", "購物", "🛍️"),
    BEAUTY("beauty", "美容", "💄"),
    MASSAGE("massage", "按摩", "💆"),
    FITNESS("fitness", "健身", "💪"),
    MEDICAL("medical", "醫療", "🏥"),
    PET("pet", "寵物", "🐾"),
    EDUCATION("education", "教育", "📚"),
    TECH("tech", "3C", "📱"),
    LODGING("lodging", "住宿", "🏨"),
    TRANSPORT("transport", "交通", "🚗"),
    COFFEE("coffee", "咖啡", "☕"),
    OTHER("other", "其他", "📌");

    companion object {
        /** id 找不到時 fallback 用 */
        val DEFAULT = OTHER

        fun fromId(id: String): BuiltInCategory? =
            entries.firstOrNull { it.id == id }

        /** UI 列出分類時用的順序（跟 enum declaration 順序一致）*/
        fun all(): List<BuiltInCategory> = entries.toList()
    }
}
