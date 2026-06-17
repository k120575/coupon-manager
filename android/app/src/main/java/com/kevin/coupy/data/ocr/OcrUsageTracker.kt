package com.kevin.coupy.data.ocr

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kevin.coupy.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 免費版 OCR 用量追蹤。
 *
 * 規格：
 * - Release：每月 [MONTHLY_LIMIT] 次，每月 1 日重置。
 * - Debug：放寬成每天 [DEBUG_DAILY_LIMIT] 次，每日重置——方便開發/測試時連續打 OCR。
 *
 * 重置都是 lazy reset：下次讀取/寫入時發現週期（月份或日期）變了就歸零。
 *
 * 儲存於 DataStore：
 * - `ocr_usage_period`：當前計數所屬週期；release 為月份 "2026-05"，debug 為日期 "2026-05-31"
 * - `ocr_usage_count`：當前週期已用次數
 */
@Singleton
class OcrUsageTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val periodKey = stringPreferencesKey(KEY_PERIOD)
    private val countKey = intPreferencesKey(KEY_COUNT)

    val monthlyUsage: Flow<MonthlyUsage> = dataStore.data.map { prefs ->
        val currentPeriod = currentPeriodString()
        val storedPeriod = prefs[periodKey]
        val count = if (storedPeriod == currentPeriod) prefs[countKey] ?: 0 else 0
        MonthlyUsage(count = count, limit = CURRENT_LIMIT, isDaily = IS_DAILY)
    }

    /** 計次 +1。若儲存的週期不是當前週期會自動重置 */
    suspend fun increment() {
        dataStore.edit { prefs ->
            val currentPeriod = currentPeriodString()
            val storedPeriod = prefs[periodKey]
            if (storedPeriod != currentPeriod) {
                prefs[periodKey] = currentPeriod
                prefs[countKey] = 1
            } else {
                prefs[countKey] = (prefs[countKey] ?: 0) + 1
            }
        }
    }

    /** 給開發測試用：歸零 */
    suspend fun resetForTesting() {
        dataStore.edit {
            it.remove(periodKey)
            it.remove(countKey)
        }
    }

    private fun currentPeriodString(): String {
        val now = LocalDate.now()
        // Debug 用每日週期、Release 用每月週期
        return if (IS_DAILY) {
            now.toString() // "2026-05-31"
        } else {
            "${now.year}-${"%02d".format(now.monthValue)}" // "2026-05"
        }
    }

    companion object {
        const val MONTHLY_LIMIT = 15
        const val DEBUG_DAILY_LIMIT = 20

        /** Debug build 改成每日計次、每天 20 次 */
        val IS_DAILY = BuildConfig.DEBUG
        val CURRENT_LIMIT = if (BuildConfig.DEBUG) DEBUG_DAILY_LIMIT else MONTHLY_LIMIT

        private const val KEY_PERIOD = "ocr_usage_period"
        private const val KEY_COUNT = "ocr_usage_count"
    }
}

data class MonthlyUsage(
    val count: Int,
    val limit: Int,
    /** true = 每日額度（debug）、false = 每月額度（release）；只影響文案顯示「今日/本月」 */
    val isDaily: Boolean = false
) {
    val remaining: Int get() = (limit - count).coerceAtLeast(0)
    val isExhausted: Boolean get() = count >= limit
}
