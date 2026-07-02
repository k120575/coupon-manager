package com.kevin.coupy.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.CouponRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val DateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/**
 * 使用紀錄頁的 ViewModel。
 *
 * 訂閱完整使用事件（最新在前）+ 搜尋字串，依票券名稱篩選後組成 UI 清單。
 * 篩選跟票券列表一致：名稱 contains（忽略大小寫），在記憶體做。
 */
@HiltViewModel
class UsageHistoryViewModel @Inject constructor(
    couponRepository: CouponRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<UsageHistoryUiState> = combine(
        couponRepository.observeUsageHistory(),
        _searchQuery
    ) { rows, query ->
        val zone = ZoneId.systemDefault()
        val q = query.trim()
        val items = rows
            .filter { q.isEmpty() || it.couponName.contains(q, ignoreCase = true) }
            .map { row ->
                UsageHistoryItem(
                    dateTime = row.usedAt.atZone(zone).format(DateTimeFormat),
                    couponName = row.couponName,
                    count = row.count
                )
            }
        UsageHistoryUiState(
            items = items,
            totalCount = rows.size,
            isLoading = false,
            isSearching = q.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = UsageHistoryUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}

// ===== UI State =====

data class UsageHistoryUiState(
    val items: List<UsageHistoryItem> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    /** 有套用搜尋字串 */
    val isSearching: Boolean = false
) {
    /** 完全沒有任何使用紀錄 */
    val hasNoHistory: Boolean get() = !isLoading && totalCount == 0

    /** 有紀錄但搜尋無結果 */
    val hasNoSearchResults: Boolean
        get() = !isLoading && isSearching && items.isEmpty() && totalCount > 0
}

/** 使用紀錄清單中的單筆：日期時間 · 票券名稱 · N 張 */
data class UsageHistoryItem(
    val dateTime: String,
    val couponName: String,
    val count: Int
)
