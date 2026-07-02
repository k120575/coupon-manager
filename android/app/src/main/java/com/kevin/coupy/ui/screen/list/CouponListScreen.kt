package com.kevin.coupy.ui.screen.list

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.ui.components.CouponActionsBottomSheet
import com.kevin.coupy.ui.components.DeleteCouponDialog
import com.kevin.coupy.ui.components.UseTicketsDialog
import com.kevin.coupy.ui.util.clearFocusOnTap
import java.io.File

/**
 * 票券 tab：完整 active 票券列表。
 *
 * - 上方：搜尋框
 * - 中段：票券列表
 * - 點卡片 → 使用票券對話框
 * - 長按卡片 / 點右側三個點 → 底部 bottom sheet（編輯 / 刪除）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponListScreen(
    innerPadding: PaddingValues,
    onCouponClick: (Long) -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: CouponListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()

    var actionSheetItem by remember { mutableStateOf<CouponListItem?>(null) }
    var useDialogItem by remember { mutableStateOf<CouponListItem?>(null) }
    var deleteDialogItem by remember { mutableStateOf<CouponListItem?>(null) }
    var expiredExpanded by remember { mutableStateOf(false) }
    var showClearExpiredDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "票券",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "使用紀錄"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
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
                // 點搜尋框以外的空白處時收起鍵盤
                .clearFocusOnTap()
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.availableCategories.isNotEmpty()) {
                CategoryFilterRow(
                    categories = uiState.availableCategories,
                    selectedCategoryId = selectedCategoryId,
                    onSelect = viewModel::onCategoryFilterChange
                )
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize())
                }
                uiState.hasNoCoupons -> {
                    EmptyState(
                        title = "還沒有票券",
                        hint = "點右下 + 新增第一張",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.hasNoSearchResults -> {
                    EmptyState(
                        title = "找不到符合的票券",
                        hint = "試試其他關鍵字或分類",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    val expired = uiState.expiredItems
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 4.dp, bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = uiState.activeItems, key = { it.id }) { item ->
                            CouponCard(
                                item = item,
                                onClick = { useDialogItem = item },
                                onLongClick = { actionSheetItem = item },
                                onMoreClick = { actionSheetItem = item }
                            )
                        }

                        // 底部「已過期」可收合區（預設收合）
                        if (expired.isNotEmpty()) {
                            item(key = "__expired_header__") {
                                ExpiredSectionHeader(
                                    count = expired.size,
                                    expanded = expiredExpanded,
                                    onToggle = { expiredExpanded = !expiredExpanded },
                                    onClearAll = { showClearExpiredDialog = true }
                                )
                            }
                            if (expiredExpanded) {
                                items(items = expired, key = { it.id }) { item ->
                                    CouponCard(
                                        item = item,
                                        onClick = { useDialogItem = item },
                                        onLongClick = { actionSheetItem = item },
                                        onMoreClick = { actionSheetItem = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== Bottom sheet + dialogs =====

    actionSheetItem?.let { item ->
        CouponActionsBottomSheet(
            item = item,
            onDismiss = { actionSheetItem = null },
            onEditClick = {
                actionSheetItem = null
                onCouponClick(item.id)
            },
            onDeleteClick = {
                deleteDialogItem = item
                actionSheetItem = null
            }
        )
    }

    useDialogItem?.let { item ->
        UseTicketsDialog(
            couponName = item.name,
            maxQuantity = item.quantity,
            photoUri = item.imagePath?.let { Uri.fromFile(File(it)) },
            onDismiss = { useDialogItem = null },
            onConfirm = { count ->
                viewModel.useCoupon(item.id, count)
                useDialogItem = null
            }
        )
    }

    deleteDialogItem?.let { item ->
        DeleteCouponDialog(
            couponName = item.name,
            onDismiss = { deleteDialogItem = null },
            onConfirm = {
                viewModel.deleteCoupon(item.id)
                deleteDialogItem = null
            }
        )
    }

    if (showClearExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showClearExpiredDialog = false },
            title = { Text("清除已過期票券", fontWeight = FontWeight.SemiBold) },
            text = { Text("將刪除這 ${uiState.expiredItems.size} 筆已過期票券，刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllExpired()
                    showClearExpiredDialog = false
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearExpiredDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 「已過期」區塊標題列：可點擊展開/收合，右側「清除全部」。
 * 整列灰階（onSurfaceVariant），跟未過期內容做視覺區隔。
 */
@Composable
private fun ExpiredSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClearAll: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收合" else "展開",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "已過期 ($count)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearAll) {
                Text("清除全部", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜尋票券名稱") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除"
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "__all__") {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onSelect(null) },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        items(items = categories, key = { it.id }) { category ->
            val isSelected = selectedCategoryId == category.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else category.id) },
                label = {
                    Text("${category.emoji} ${category.displayName}")
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalActivity,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
