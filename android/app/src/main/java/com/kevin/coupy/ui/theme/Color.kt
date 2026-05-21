package com.kevin.coupy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 券管家 Coupy 品牌色票
 *
 * 主色：柔和藍 CoupyTeal (歷史變數名沿用；實際是 soft blue)
 * 副色：珊瑚紅 CoupyCoral
 * 背景：深黑 / 純白
 *
 * 主色已從青綠改為柔和藍，原因：Kevin 偏好藍系。
 * 副色維持珊瑚紅——藍紅是經典暖冷互補，讓「即將過期」珊瑚紅警示依然醒目。
 */

// === 品牌主色（明亮柔藍）===
val CoupyTeal = Color(0xFF8BB8F7)           // 主色（亮但不刺眼，藍味清楚）
val CoupyTealDim = Color(0xFF5C8FD0)        // 較暗版（淺底文字 / 淺底主色用）
val CoupyCoral = Color(0xFFFF6B6B)
val CoupyCoralSoft = Color(0xFFFF8A80)      // 較柔版（淺底使用）

// === 背景 / Surface ===
val CoupyBlack = Color(0xFF0F0F0F)          // 深色主背景
val CoupyDarkSurface = Color(0xFF1A1A1A)    // 深色卡片
val CoupyDarkSurfaceVariant = Color(0xFF252525)
val CoupyWhite = Color(0xFFFAFAFA)          // 淺色主背景
val CoupyLightSurface = Color(0xFFFFFFFF)
val CoupyLightSurfaceVariant = Color(0xFFF0F0F0)

// === 文字 ===
val CoupyOnDark = Color(0xFFFFFFFF)
val CoupyOnDarkSecondary = Color(0xFFB0B0B0)
val CoupyOnLight = Color(0xFF0F0F0F)
val CoupyOnLightSecondary = Color(0xFF555555)

// === 狀態色 ===
val CoupyExpireSoon = Color(0xFFFF6B6B)     // 即將過期警示（同副色）
val CoupySuccess = Color(0xFF8BB8F7)        // 成功（同主色）
val CoupyError = Color(0xFFE63946)
val CoupyWarning = Color(0xFFF4A261)

// === 邊框 / 分隔線 ===
val CoupyDarkOutline = Color(0xFF333333)
val CoupyLightOutline = Color(0xFFE0E0E0)
