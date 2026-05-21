package com.kevin.coupy.data.category

/**
 * UI 消費的分類模型——已套用「使用者自訂名稱（如果有）」的最終樣貌。
 *
 * 由 CategoryRepository 組合 BuiltInCategory + DataStore 中的自訂名稱產生。
 */
data class Category(
    val id: String,
    /** 顯示名稱：如果使用者重新命名過，會是新名字；否則是預設名 */
    val displayName: String,
    /** 預設名稱（提示「原本叫什麼」用，例如設定頁顯示）*/
    val defaultName: String,
    val emoji: String,
    /** true 表示使用者重新命名過 */
    val isCustomized: Boolean
) {
    val hasBeenRenamed: Boolean get() = isCustomized
}
