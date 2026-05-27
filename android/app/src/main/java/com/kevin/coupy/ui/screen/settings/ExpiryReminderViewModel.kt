package com.kevin.coupy.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.notification.ExpiryReminderPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 設定頁的到期提醒勾選狀態。
 */
@HiltViewModel
class ExpiryReminderViewModel @Inject constructor(
    private val repository: ExpiryReminderPreferenceRepository
) : ViewModel() {

    val enabledDays: StateFlow<Set<Int>> = repository.enabledDays.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpiryReminderPreferenceRepository.DEFAULT
    )

    fun toggleDay(day: Int, enabled: Boolean) {
        viewModelScope.launch {
            val current = enabledDays.value
            val next = if (enabled) current + day else current - day
            repository.setEnabledDays(next)
        }
    }
}
