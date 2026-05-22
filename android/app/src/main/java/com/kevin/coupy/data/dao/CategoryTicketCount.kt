package com.kevin.coupy.data.dao

/**
 * 統計頁分類分佈查詢的回傳型別。
 *
 * category 是分類 id（不是 displayName）；UI 層用 CategoryRepository 把 id 翻譯成顯示名稱。
 */
data class CategoryTicketCount(
    val category: String,
    val total: Int
)
