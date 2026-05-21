package com.kevin.coupy.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kevin.coupy.ui.navigation.Routes

/**
 * 底部導航列。
 *
 * 4 個 tab：首頁 / 票券 / 統計 / 我的。
 * 選中時用品牌主色強調 icon 與文字。
 */
@Composable
fun CoupyBottomBar(
    currentRoute: String?,
    onTabClick: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onTabClick(tab.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.iconSelected else tab.iconUnselected,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private enum class BottomTab(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
) {
    HOME(Routes.HOME, "首頁", Icons.Filled.Home, Icons.Outlined.Home),
    TICKETS(Routes.TICKETS, "票券", Icons.Outlined.LocalActivity, Icons.Outlined.LocalActivity),
    STATS(Routes.STATS, "統計", Icons.Outlined.BarChart, Icons.Outlined.BarChart),
    MINE(Routes.MINE, "我的", Icons.Filled.Person, Icons.Outlined.Person);
}
