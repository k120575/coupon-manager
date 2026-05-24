package com.kevin.coupy.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin.coupy.ui.components.CoupyBottomBar
import com.kevin.coupy.ui.navigation.Routes
import com.kevin.coupy.ui.screen.edit.CouponEditScreen
import com.kevin.coupy.ui.screen.home.HomeScreen
import com.kevin.coupy.ui.screen.list.CouponListScreen
import com.kevin.coupy.ui.screen.mine.MineScreen
import com.kevin.coupy.ui.screen.backup.BackupScreen
import com.kevin.coupy.ui.screen.donate.DonateScreen
import com.kevin.coupy.ui.screen.settings.AboutScreen
import com.kevin.coupy.ui.screen.settings.CategoryManagementScreen
import com.kevin.coupy.ui.screen.stats.StatsScreen

/**
 * 頂層 Composable。
 *
 * 結構：
 *   Scaffold
 *     ├── topBar：由各畫面自己提供（不在這層）
 *     ├── bottomBar：4 個 tab 的 NavigationBar，只在 tab 畫面顯示
 *     ├── floatingActionButton：+ 新增，只在 HOME / TICKETS 顯示
 *     └── NavHost：所有路由
 *
 * 子畫面（edit / category / backup / about / donate）會自動隱藏 bottomBar 與 FAB。
 */
@Composable
fun CoupyApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in Routes.BOTTOM_TABS
    val showFab = currentRoute in Routes.FAB_TABS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                CoupyBottomBar(
                    currentRoute = currentRoute,
                    onTabClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.editRoute()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新增票券"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
        ) {
            // ===== Tab Screens =====
            composable(Routes.HOME) {
                HomeScreen(
                    innerPadding = innerPadding,
                    onNavigateToTickets = {
                        navController.navigate(Routes.TICKETS) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Routes.TICKETS) {
                CouponListScreen(
                    innerPadding = innerPadding,
                    onCouponClick = { id -> navController.navigate(Routes.editRoute(id)) }
                )
            }

            composable(Routes.STATS) {
                StatsScreen(innerPadding = innerPadding)
            }

            composable(Routes.MINE) {
                MineScreen(
                    innerPadding = innerPadding,
                    onCategoryManagementClick = { navController.navigate(Routes.CATEGORY_MANAGEMENT) },
                    onBackupClick = { navController.navigate(Routes.BACKUP) },
                    onDonateClick = { navController.navigate(Routes.DONATE) },
                    onAboutClick = { navController.navigate(Routes.ABOUT) }
                )
            }

            // ===== Detail Screens =====
            composable(
                route = Routes.EDIT_ROUTE,
                arguments = listOf(
                    navArgument(Routes.EDIT_ARG_COUPON_ID) {
                        type = NavType.LongType
                        defaultValue = Routes.EDIT_NO_ID_SENTINEL
                    }
                )
            ) {
                CouponEditScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CATEGORY_MANAGEMENT) {
                CategoryManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.BACKUP) {
                BackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.DONATE) {
                DonateScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
