package com.kevin.coupy.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.CouponRepository
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.data.category.CategoryRepository
import com.kevin.coupy.data.entity.CouponEntity
import com.kevin.coupy.ui.screen.list.CouponListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * 首頁 Dashboard 的 ViewModel。
 *
 * 訂閱所有 active 票券 + 分類，計算統計數據與「即將到期前 3 名」。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    couponRepository: CouponRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        couponRepository.observeActive(),
        categoryRepository.observeAll()
    ) { coupons, categories ->
        val today = LocalDate.now()
        val categoryById = categories.associateBy { it.id }

        val daysMap: Map<CouponEntity, Long> = coupons.associateWith {
            ChronoUnit.DAYS.between(today, it.expireDate)
        }

        HomeUiState(
            totalTicketCount = coupons.sumOf { it.quantity },
            distinctCategoryCount = coupons.map { it.category }.distinct().size,
            // 非重疊分桶：每張票券落在唯一一個區間
            expiringIn3Count = daysMap.filterByDays(0L..3L),
            expiringIn7Count = daysMap.filterByDays(4L..7L),
            expiringIn30Count = daysMap.filterByDays(8L..30L),
            expiredCount = daysMap.filterByDays(Long.MIN_VALUE..-1L),
            topExpiring = coupons
                .filter { (daysMap[it] ?: Long.MAX_VALUE) >= 0 }
                .sortedBy { it.expireDate }
                .take(3)
                .map { it.toListItem(categoryById, today) },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    private fun Map<CouponEntity, Long>.filterByDays(range: LongRange): Int =
        entries.filter { it.value in range }.sumOf { it.key.quantity }
}

// ===== UI State =====

data class HomeUiState(
    val totalTicketCount: Int = 0,
    val distinctCategoryCount: Int = 0,
    val expiringIn3Count: Int = 0,
    val expiringIn7Count: Int = 0,
    val expiringIn30Count: Int = 0,
    val expiredCount: Int = 0,
    val topExpiring: List<CouponListItem> = emptyList(),
    val isLoading: Boolean = false
) {
    val hasNoCoupons: Boolean
        get() = !isLoading && totalTicketCount == 0
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
