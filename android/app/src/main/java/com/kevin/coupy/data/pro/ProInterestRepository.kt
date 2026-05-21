package com.kevin.coupy.data.pro

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pro 付費意願訊號儲存。
 *
 * 使用者第一次點「通知我上線」時記錄一個 timestamp，之後再進入頁面顯示「已登記」狀態。
 * 這是 v1.0 最重要的訊號之一——決定要不要真的做 Pro 的依據。
 *
 * 目前只儲存「是否點過 + 點的時間」。v1.1+ 可以擴充：
 * - 點擊當下的票券數（推估這人對 Pro 多有需求）
 * - 點擊時 OCR 是否撞牆過
 * - email（如果做 email 收集 flow）
 */
@Singleton
class ProInterestRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val signaledAtKey = longPreferencesKey(KEY_SIGNALED_AT)

    /** 是否已點過「通知我上線」*/
    val hasSignaled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[signaledAtKey] != null
    }

    /** 點擊時間（epoch millis）；null = 還沒點過 */
    val signaledAt: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[signaledAtKey]
    }

    /** 記錄訊號。已記錄過不會覆蓋（保留首次時間） */
    suspend fun signalInterest() {
        dataStore.edit { prefs ->
            if (prefs[signaledAtKey] == null) {
                prefs[signaledAtKey] = System.currentTimeMillis()
            }
        }
    }

    /** 給未來「重置已登記狀態」用（測試或設定裡的選項）*/
    suspend fun reset() {
        dataStore.edit { it.remove(signaledAtKey) }
    }

    suspend fun getSignaledAtSync(): Long? = dataStore.data.first()[signaledAtKey]

    companion object {
        private const val KEY_SIGNALED_AT = "pro_interest_signaled_at"
    }
}
