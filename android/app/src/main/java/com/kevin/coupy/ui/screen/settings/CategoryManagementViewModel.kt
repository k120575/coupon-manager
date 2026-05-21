package com.kevin.coupy.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.data.category.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()

    fun rename(categoryId: String, newName: String) {
        viewModelScope.launch {
            try {
                categoryRepository.rename(categoryId, newName)
            } catch (e: IllegalArgumentException) {
                _errorEvent.send(e.message ?: "改名失敗")
            }
        }
    }

    fun resetToDefault(categoryId: String) {
        viewModelScope.launch {
            categoryRepository.resetToDefault(categoryId)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = CategoryRepository.MAX_NAME_LENGTH
    }
}
