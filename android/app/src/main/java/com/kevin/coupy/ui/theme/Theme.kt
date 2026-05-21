package com.kevin.coupy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 券管家 Coupy 主題
 *
 * 不啟用 Material You dynamicColor——品牌色票需要保持一致，不要被系統壁紙取代。
 * 預設跟隨系統深淺模式；深色為主要視覺方向（與 logo_compare.html B 對齊）。
 */

private val CoupyDarkColors = darkColorScheme(
    primary = CoupyTeal,
    onPrimary = CoupyBlack,
    primaryContainer = CoupyTealDim,
    onPrimaryContainer = CoupyOnDark,

    secondary = CoupyCoral,
    onSecondary = CoupyOnDark,
    secondaryContainer = CoupyCoral,
    onSecondaryContainer = CoupyOnDark,

    tertiary = CoupyCoralSoft,
    onTertiary = CoupyBlack,

    background = CoupyBlack,
    onBackground = CoupyOnDark,
    surface = CoupyDarkSurface,
    onSurface = CoupyOnDark,
    surfaceVariant = CoupyDarkSurfaceVariant,
    onSurfaceVariant = CoupyOnDarkSecondary,

    error = CoupyError,
    onError = CoupyOnDark,

    outline = CoupyDarkOutline
)

private val CoupyLightColors = lightColorScheme(
    primary = CoupyTealDim,
    onPrimary = CoupyOnDark,
    primaryContainer = CoupyTeal,
    onPrimaryContainer = CoupyOnLight,

    secondary = CoupyCoral,
    onSecondary = CoupyOnDark,
    secondaryContainer = CoupyCoralSoft,
    onSecondaryContainer = CoupyOnLight,

    tertiary = CoupyCoralSoft,
    onTertiary = CoupyOnLight,

    background = CoupyWhite,
    onBackground = CoupyOnLight,
    surface = CoupyLightSurface,
    onSurface = CoupyOnLight,
    surfaceVariant = CoupyLightSurfaceVariant,
    onSurfaceVariant = CoupyOnLightSecondary,

    error = CoupyError,
    onError = CoupyOnDark,

    outline = CoupyLightOutline
)

@Composable
fun CoupyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CoupyDarkColors else CoupyLightColors

    // 同步 status bar 圖示顏色（淺底用深色 icon、深底用淺色 icon）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CoupyTypography,
        content = content
    )
}
