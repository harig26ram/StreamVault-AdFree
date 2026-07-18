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
class SearchViewModel @Inject constructor(
    private val repository: StreamRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<StreamItem>>(emptyList())
    val results: StateFlow<List<StreamItem>> = _results.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onQueryChanged(query: String) {
        _query.value = query
        if (query.isNotEmpty()) {
            loadSuggestions(query)
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun search(query: String) {
        _query.value = query
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.search(query)
                .onSuccess { items ->
                    _results.value = items
                    _isLoading.value = false
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Search failed"
                    _isLoading.value = false
                }
        }
    }

    private fun loadSuggestions(query: String) {
        if (query.length < 2) {
            _suggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            repository.getSuggestions(query)
                .onSuccess { _suggestions.value = it }
                .onFailure { _suggestions.value = emptyList() }
        }
    }

    fun clearSearch() {
        _query.value = ""
        _results.value = emptyList()
        _suggestions.value = emptyList()
        _error.value = null
    }
}
