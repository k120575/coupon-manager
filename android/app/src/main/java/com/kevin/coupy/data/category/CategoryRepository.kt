package com.kevin.coupy.data.category

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分類資料來源。
 *
 * 內部組合：
 * - BuiltInCategory（固定 14 個，硬編碼）
 * - DataStore Preferences（儲存「使用者把某個 id 改名成 X」的 override）
 *
 * v1.0 限制：
 * - 不能新增分類（Pro 才能）
 * - 不能刪除分類（避免使用者誤刪、孤兒票券）
 * - 改名長度 1-8 字
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** 訂閱所有分類（含使用者自訂名稱）*/
    fun observeAll(): Flow<List<Category>> =
        dataStore.data.map { prefs ->
            BuiltInCategory.all().map { it.toCategory(prefs) }
        }

    /** 取單一分類（一次性，不訂閱）*/
    suspend fun getById(id: String): Category {
        val prefs = dataStore.data.first()
        val builtIn = BuiltInCategory.fromId(id) ?: BuiltInCategory.DEFAULT
        return builtIn.toCategory(prefs)
    }

    /** 取顯示名稱（一次性）*/
    suspend fun getDisplayName(id: String): String = getById(id).displayName

    /**
     * 重新命名分類。
     *
     * @throws IllegalArgumentException 名稱長度不在 1-8 字、或全為空白
     */
    suspend fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "名稱不能為空" }
        require(trimmed.length <= MAX_NAME_LENGTH) { "名稱最多 $MAX_NAME_LENGTH 個字" }

        val builtIn = BuiltInCategory.fromId(id)
            ?: throw IllegalArgumentException("Unknown category id: $id")

        dataStore.edit { prefs ->
            if (trimmed == builtIn.defaultName) {
                // 跟預設一樣 → 直接清除 override，省 storage
                prefs.remove(nameKey(id))
            } else {
                prefs[nameKey(id)] = trimmed
            }
        }
    }

    /** 重置為預設名稱 */
    suspend fun resetToDefault(id: String) {
        dataStore.edit { it.remove(nameKey(id)) }
    }

    /** 清除所有自訂名稱（給設定頁的「全部還原預設」用，可選）*/
    suspend fun resetAll() {
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith(KEY_PREFIX) }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }

    // ===== private helpers =====

    private fun BuiltInCategory.toCategory(prefs: Preferences): Category {
        val customName = prefs[nameKey(id)]
        return Category(
            id = id,
            displayName = customName ?: defaultName,
            defaultName = defaultName,
            emoji = emoji,
            isCustomized = customName != null
        )
    }

    private fun nameKey(id: String) = stringPreferencesKey("$KEY_PREFIX$id")

    companion object {
        const val MAX_NAME_LENGTH = 8
        private const val KEY_PREFIX = "category_name_"
    }
}
