package com.kevin.coupy.ui.screen.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.CouponRepository
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.data.category.CategoryRepository
import com.kevin.coupy.data.entity.CouponEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * 主畫面（票券列表）的 ViewModel。
 *
 * 訂閱 active 票券 + 所有分類 + 搜尋字串，組裝成 UI 可直接 render 的 CouponListUiState。
 */
@HiltViewModel
class CouponListViewModel @Inject constructor(
    private val couponRepository: CouponRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<CouponListUiState> = combine(
        couponRepository.observeActive(),
        categoryRepository.observeAll(),
        _searchQuery
    ) { coupons, categories, query ->
        val categoryById = categories.associateBy { it.id }
        val today = LocalDate.now()
        val filtered = if (query.isBlank()) {
            coupons
        } else {
            val q = query.trim()
            coupons.filter { it.name.contains(q, ignoreCase = true) }
        }
        CouponListUiState(
            items = filtered.map { it.toListItem(categoryById, today) },
            totalCount = coupons.size,
            isLoading = false,
            isSearching = query.isNotBlank()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = CouponListUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun useCoupon(id: Long, count: Int = 1) {
        viewModelScope.launch {
            couponRepository.use(id, count)
        }
    }

    fun deleteCoupon(id: Long) {
        viewModelScope.launch {
            couponRepository.delete(id)
        }
    }
}

// ===== UI State =====

data class CouponListUiState(
    val items: List<CouponListItem> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false
) {
    /** 完全沒票券（不管有沒有搜尋）*/
    val hasNoCoupons: Boolean get() = !isLoading && totalCount == 0

    /** 有票券但搜尋無結果 */
    val hasNoSearchResults: Boolean
        get() = !isLoading && isSearching && items.isEmpty() && totalCount > 0
}

/**
 * 列表中單張票券的 UI 表示。已預先 resolve 分類顯示名、emoji、到期天數。
 */
data class CouponListItem(
    val id: Long,
    val name: String,
    val expireDate: LocalDate,
    val categoryDisplayName: String,
    val categoryEmoji: String,
    val quantity: Int,
    /** 距離到期還有幾天。負數 = 已過期；0 = 今天到期 */
    val daysUntilExpire: Long
) {
    val isExpiringSoon: Boolean get() = daysUntilExpire in 0..7
    val isExpired: Boolean get() = daysUntilExpire < 0
}

// ===== mapper =====

private fun CouponEntity.toListItem(
    categoryById: Map<String, Category>,
    today: LocalDate
): CouponListItem {
    val category = categoryById[this.category]
    return CouponListItem(
        id = id,
        name = name,
        expireDate = expireDate,
        categoryDisplayName = category?.displayName ?: this.category,
        categoryEmoji = category?.emoji ?: "📌",
        quantity = quantity,
        daysUntilExpire = ChronoUnit.DAYS.between(today, expireDate)
    )
}
