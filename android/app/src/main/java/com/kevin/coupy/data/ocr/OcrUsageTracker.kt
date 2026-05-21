package com.kevin.coupy.data.ocr

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 免費版 OCR 用量追蹤。
 *
 * 規格：每月 [MONTHLY_LIMIT] 次，每月 1 日重置（lazy reset：下次讀取/寫入時發現月份變了就歸零）。
 *
 * 儲存於 DataStore：
 * - `ocr_usage_period`：當前計數所屬月份，格式 "2026-05"
 * - `ocr_usage_count`：當月已用次數
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
        MonthlyUsage(count = count, limit = MONTHLY_LIMIT)
    }

    /** 計次 +1。若儲存的月份不是當月會自動重置 */
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
        return "${now.year}-${"%02d".format(now.monthValue)}"
    }

    companion object {
        const val MONTHLY_LIMIT = 5
        private const val KEY_PERIOD = "ocr_usage_period"
        private const val KEY_COUNT = "ocr_usage_count"
    }
}

data class MonthlyUsage(
    val count: Int,
    val limit: Int
) {
    val remaining: Int get() = (limit - count).coerceAtLeast(0)
    val isExhausted: Boolean get() = count >= limit
}
