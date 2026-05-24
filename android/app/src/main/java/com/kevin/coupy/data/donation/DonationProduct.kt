package com.kevin.coupy.data.donation

/**
 * Donate 用的 3 個 IAP 商品。
 *
 * Google Play Console 需要設定對應的 In-app Product（Consumable）：
 *   coupy_tip_coffee  → NT$100
 *   coupy_tip_meal    → NT$200
 *   coupy_tip_weekend → NT$300
 *
 * 全部都是 Consumable——使用者買完立刻消費，可重複購買。
 */
enum class DonationProduct(
    val productId: String,
    val emoji: String,
    val label: String
) {
    COFFEE("coupy_tip_coffee", "☕", "一杯咖啡"),
    MEAL("coupy_tip_meal", "🍱", "一餐飯"),
    WEEKEND("coupy_tip_weekend", "💻", "一個週末");

    companion object {
        fun fromProductId(productId: String): DonationProduct? =
            entries.firstOrNull { it.productId == productId }
    }
}
