package com.kevin.coupy.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外觀偏好儲存（system / light / dark）。
 */
@Singleton
class ThemePreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val themeModeKey = stringPreferencesKey(KEY_THEME_MODE)

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromStorageValue(prefs[themeModeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.storageValue
        }
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
