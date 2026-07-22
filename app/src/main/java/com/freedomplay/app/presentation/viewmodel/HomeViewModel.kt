package com.freedomplay.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.repository.StreamRepository
import com.freedomplay.app.domain.model.StreamItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: StreamRepository
) : ViewModel() {

    private val _trending = MutableStateFlow<List<StreamItem>>(emptyList())
    val trending: StateFlow<List<StreamItem>> = _trending.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _allTrending = MutableStateFlow<List<StreamItem>>(emptyList())

    init {
        loadTrending()
    }

    fun loadTrending() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getTrending()
                .onSuccess { items ->
                    _allTrending.value = items
                    applyCategoryFilter()
                    _isLoading.value = false
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load trending"
                    _isLoading.value = false
                }
        }
    }

    fun refresh() {
        loadTrending()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        applyCategoryFilter()
    }

    private fun applyCategoryFilter() {
        val category = _selectedCategory.value
        val all = _allTrending.value
        _trending.value = if (category == "All") {
            all
        } else {
            all.filter { item ->
                item.title.contains(category, ignoreCase = true) ||
                    item.uploaderName.contains(category, ignoreCase = true)
            }
        }
    }
}
