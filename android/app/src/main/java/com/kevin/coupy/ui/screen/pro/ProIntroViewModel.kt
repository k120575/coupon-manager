package com.kevin.coupy.ui.screen.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.pro.ProInterestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProIntroViewModel @Inject constructor(
    private val proInterestRepository: ProInterestRepository
) : ViewModel() {

    val hasSignaled: StateFlow<Boolean> = proInterestRepository.hasSignaled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun signalInterest() {
        viewModelScope.launch {
            proInterestRepository.signalInterest()
        }
    }

    fun cancelInterest() {
        viewModelScope.launch {
            proInterestRepository.reset()
        }
    }
}
