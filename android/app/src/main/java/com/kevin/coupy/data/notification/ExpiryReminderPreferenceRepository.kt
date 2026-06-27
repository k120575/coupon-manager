package com.kevin.coupy.data.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 到期提醒偏好：使用者可勾選想在到期前幾天收到推播。
 *
 * 允許值：1 / 3 / 7 / 30。多選。空集合代表完全不提醒。
 * 預設啟用 3 天 + 7 天。
 */
@Singleton
class ExpiryReminderPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val enabledDaysKey = stringSetPreferencesKey(KEY_ENABLED_DAYS)
    private val lastRunDateKey = stringPreferencesKey(KEY_LAST_RUN_DATE)

    val enabledDays: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[enabledDaysKey]
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in OPTIONS }
            ?.toSet()
            ?: DEFAULT
    }

    suspend fun setEnabledDays(days: Set<Int>) {
        val sanitized = days.filter { it in OPTIONS }.map { it.toString() }.toSet()
        dataStore.edit { prefs ->
            prefs[enabledDaysKey] = sanitized
        }
    }

    /**
     * 上次成功跑提醒檢查的日期（null = 還沒跑過）。
     * 用來算距上次的天數差，補償 Doze/省電被延後或整天沒跑時漏掉的門檻。
     */
    val lastRunDate: Flow<LocalDate?> = dataStore.data.map { prefs ->
        prefs[lastRunDateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    suspend fun setLastRunDate(date: LocalDate) {
        dataStore.edit { prefs ->
            prefs[lastRunDateKey] = date.toString()
        }
    }

    companion object {
        private const val KEY_ENABLED_DAYS = "expiry_reminder_enabled_days"
        private const val KEY_LAST_RUN_DATE = "expiry_reminder_last_run_date"

        /** 可勾選的選項（排序固定，UI 直接吃這個）*/
        val OPTIONS = listOf(1, 3, 7, 30)

        /** 預設：到期前 3 天 + 7 天，避免使用者一裝 app 就被推播炸 */
        val DEFAULT = setOf(3, 7)
    }
}
