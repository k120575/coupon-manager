package com.kevin.coupy.data.theme

/**
 * 使用者外觀偏好。
 *
 * SYSTEM = 跟隨系統（預設）；LIGHT / DARK = 強制覆寫。
 */
enum class ThemeMode(val storageValue: String, val displayName: String) {
    SYSTEM("system", "跟隨系統"),
    LIGHT("light", "淺色"),
    DARK("dark", "深色");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
