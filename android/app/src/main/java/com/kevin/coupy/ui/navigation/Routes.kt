package com.kevin.coupy.ui.navigation

/**
 * 路由常數。
 *
 * 結構：
 * - 4 個底部 tab：HOME / TICKETS / STATS / MINE
 * - 子頁面：EDIT（新增/編輯）、SETTINGS（從 MINE 進入）
 *
 * Tab 切換時保留各自的 back stack；子頁面共用上層導航堆疊。
 */
object Routes {

    // ===== Bottom Tabs =====
    const val HOME = "home"
    const val TICKETS = "tickets"
    const val STATS = "stats"
    const val MINE = "mine"

    val BOTTOM_TABS = setOf(HOME, TICKETS, STATS, MINE)

    /** 顯示 FAB 的 tab（新增票券）*/
    val FAB_TABS = setOf(HOME, TICKETS)

    // ===== Detail Pages =====

    const val EDIT = "edit"
    const val EDIT_ARG_COUPON_ID = "couponId"
    const val EDIT_ROUTE = "$EDIT?$EDIT_ARG_COUPON_ID={$EDIT_ARG_COUPON_ID}"
    const val EDIT_NO_ID_SENTINEL = -1L

    fun editRoute(couponId: Long? = null): String =
        if (couponId == null) EDIT else "$EDIT?$EDIT_ARG_COUPON_ID=$couponId"

    const val CATEGORY_MANAGEMENT = "category_management"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val DONATE = "donate"
}
