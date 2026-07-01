package com.kevin.coupy.ui.screen.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.kevin.coupy.BuildConfig
import com.kevin.coupy.data.notification.ExpiryReminderPreferenceRepository
import com.kevin.coupy.data.theme.ThemeMode
import com.kevin.coupy.notification.NotificationScheduler
import com.kevin.coupy.ui.screen.settings.ExpiryReminderViewModel
import com.kevin.coupy.ui.theme.ThemeViewModel

/**
 * 設定 tab（內部仍叫 MineScreen 是因為 route `mine` 還沒重新命名）。
 *
 * 直接列出所有設定項目，沒有獨立中間頁：
 *     ☀️ 顯示外觀
 *     🔔 到期提醒
 *     📁 分類管理
 *     💾 資料備份
 *     ☕ 請我喝杯咖啡
 *     ✉️ 問題反應與建議
 *     ℹ 關於
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    innerPadding: PaddingValues,
    onCategoryManagementClick: () -> Unit,
    onBackupClick: () -> Unit,
    onDonateClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onAboutClick: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    expiryReminderViewModel: ExpiryReminderViewModel = hiltViewModel()
) {
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val enabledReminderDays by expiryReminderViewModel.enabledDays.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "設定",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Brightness6,
                        title = "顯示外觀",
                        subtitle = themeMode.displayName,
                        onClick = { showThemeDialog = true }
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "到期提醒",
                        subtitle = reminderSubtitle(enabledReminderDays),
                        onClick = { showReminderDialog = true }
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Category,
                        title = "分類管理",
                        subtitle = "重新命名 14 個內建分類",
                        onClick = onCategoryManagementClick
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Backup,
                        title = "資料備份",
                        subtitle = "備份與還原票券資料",
                        onClick = onBackupClick
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Coffee,
                        title = "請我喝杯咖啡",
                        subtitle = "如果這個 App 幫到你了",
                        onClick = onDonateClick
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Feedback,
                        title = "問題反應與建議",
                        subtitle = "有想法或遇到問題都歡迎告訴我",
                        onClick = onFeedbackClick
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "關於",
                        subtitle = "版本資訊與隱私權",
                        onClick = onAboutClick
                    )
                    if (BuildConfig.DEBUG) {
                        RowDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Science,
                            title = "🧪 測試到期通知",
                            subtitle = "立即執行一次 ExpiryReminderWorker（debug only）",
                            onClick = {
                                NotificationScheduler.runOnce(context)
                                Toast.makeText(
                                    context,
                                    "已觸發 — 若有票券命中提醒天數，通知將跳出",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "券管家 Coupy v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        if (showThemeDialog) {
            ThemeModeDialog(
                current = themeMode,
                onDismiss = { showThemeDialog = false },
                onSelect = { mode ->
                    themeViewModel.setThemeMode(mode)
                    showThemeDialog = false
                }
            )
        }

        if (showReminderDialog) {
            ExpiryReminderDialog(
                enabledDays = enabledReminderDays,
                onToggle = expiryReminderViewModel::toggleDay,
                onDismiss = { showReminderDialog = false }
            )
        }
    }
}

private fun reminderSubtitle(enabledDays: Set<Int>): String {
    if (enabledDays.isEmpty()) return "未開啟"
    val sorted = enabledDays.sorted().joinToString("、") { "${it}天" }
    return "到期前 $sorted 提醒"
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ExpiryReminderDialog(
    enabledDays: Set<Int>,
    onToggle: (day: Int, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "到期提醒", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(
                    text = "勾選想要在到期前幾天收到推播",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExpiryReminderPreferenceRepository.OPTIONS.forEach { day ->
                    val checked = day in enabledDays
                    Surface(
                        onClick = { onToggle(day, !checked) },
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(day, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "到期前 $day 天",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "顯示外觀", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Surface(
                        onClick = { onSelect(mode) },
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == current,
                                onClick = { onSelect(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
