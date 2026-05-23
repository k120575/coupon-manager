package com.kevin.coupy.ui.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.CouponRepository
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.data.category.CategoryRepository
import com.kevin.coupy.data.dao.CategoryTicketCount
import com.kevin.coupy.data.entity.CouponEntity
import com.kevin.coupy.data.isNoExpiration
import com.kevin.coupy.data.stats.ArchetypeState
import com.kevin.coupy.data.stats.matchArchetype
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    couponRepository: CouponRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = run {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMonthStart = today.withDayOfMonth(1).plusMonths(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        // 5 個 Flow combine 後再跟 archetype Flow 第二層 combine——kotlinx coroutines
        // 沒有 6-arg typed combine，nested 比 vararg cast 乾淨
        val coreFlow = combine(
            couponRepository.observeActive(),
            couponRepository.observeUsedTicketsAllTime(),
            couponRepository.observeUsedTicketsInRange(monthStart, nextMonthStart),
            couponRepository.observeCategoryDistribution(),
            categoryRepository.observeAll()
        ) { active, usedAll, usedMonth, dist, cats ->
            StatsCoreData(active, usedAll, usedMonth, dist, cats)
        }

        combine(coreFlow, couponRepository.observeUsageByCategory()) { core, usageByCategory ->
            val activeCount = core.activeCoupons.sumOf { it.quantity }
            val days: Map<CouponEntity, Long> = core.activeCoupons
                .filter { !it.expireDate.isNoExpiration() }
                .associateWith { ChronoUnit.DAYS.between(today, it.expireDate) }

            val foreverCount = core.activeCoupons
                .filter { it.expireDate.isNoExpiration() }
                .sumOf { it.quantity }

            val categoryById = core.categories.associateBy { it.id }
            val totalForBars = core.distribution.sumOf { it.total }.coerceAtLeast(1)
            val bars = core.distribution.map { row ->
                val cat = categoryById[row.category]
                CategoryBar(
                    categoryId = row.category,
                    displayName = cat?.displayName ?: row.category,
                    emoji = cat?.emoji ?: "📌",
                    count = row.total,
                    percentage = row.total.toFloat() / totalForBars
                )
            }

            StatsUiState(
                isLoading = false,
                isEmpty = activeCount == 0 && core.usedAllTime == 0,
                activeCount = activeCount,
                usedThisMonthCount = core.usedThisMonth,
                usedAllTimeCount = core.usedAllTime,
                // 非重疊分桶（跟 [[HomeViewModel]] 一致）：實際區間 0-3 / 4-7 / 8-30。
                // 文案在 UI 層顯示為「3 天內 / 7 天內 / 30 天內」是使用者最直覺的講法。
                expiringIn3Count = days.sumQuantityIn(0L..3L),
                expiringIn7Count = days.sumQuantityIn(4L..7L),
                expiringIn30Count = days.sumQuantityIn(8L..30L),
                expiredCount = days.sumQuantityIn(Long.MIN_VALUE..-1L),
                foreverCount = foreverCount,
                categoryBars = bars,
                archetypeState = matchArchetype(usageByCategory)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = StatsUiState(isLoading = true)
        )
    }
}

private fun Map<CouponEntity, Long>.sumQuantityIn(range: LongRange): Int =
    entries.filter { it.value in range }.sumOf { it.key.quantity }

/** 第一層 combine 的中介資料——避免 6-arg combine 不存在的問題 */
private data class StatsCoreData(
    val activeCoupons: List<CouponEntity>,
    val usedAllTime: Int,
    val usedThisMonth: Int,
    val distribution: List<CategoryTicketCount>,
    val categories: List<Category>
)

data class StatsUiState(
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val activeCount: Int = 0,
    val usedThisMonthCount: Int = 0,
    val usedAllTimeCount: Int = 0,
    val expiringIn3Count: Int = 0,
    val expiringIn7Count: Int = 0,
    val expiringIn30Count: Int = 0,
    val expiredCount: Int = 0,
    val foreverCount: Int = 0,
    val categoryBars: List<CategoryBar> = emptyList(),
    val archetypeState: ArchetypeState? = null
)

data class CategoryBar(
    val categoryId: String,
    val displayName: String,
    val emoji: String,
    val count: Int,
    val percentage: Float
)
