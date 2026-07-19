package com.freedomplay.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.preferences.PreferencesManager
import com.freedomplay.app.data.repository.StreamRepository
import com.freedomplay.app.domain.model.StreamItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: StreamRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val PAGE_SIZE = 20

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<StreamItem>>(emptyList())
    val results: StateFlow<List<StreamItem>> = _results.asStateFlow()

    private val _displayedResults = MutableStateFlow<List<StreamItem>>(emptyList())
    val displayedResults: StateFlow<List<StreamItem>> = _displayedResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _trending = MutableStateFlow<List<StreamItem>>(emptyList())
    val trending: StateFlow<List<StreamItem>> = _trending.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    val searchHistory: StateFlow<List<String>> = preferencesManager.searchHistory
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private var suggestionJob: Job? = null
    private var allResults = emptyList<StreamItem>()
    private var currentOffset = 0

    init {
        loadTrending()
    }

    fun onQueryChanged(query: String) {
        _query.value = query
        suggestionJob?.cancel()
        if (query.isNotEmpty() && query.length >= 2) {
            suggestionJob = viewModelScope.launch {
                delay(300)
                loadSuggestions(query)
            }
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
            _displayedResults.value = emptyList()
            allResults = emptyList()
            currentOffset = 0
            preferencesManager.addSearchHistory(query)
            repository.search(query)
                .onSuccess { items ->
                    allResults = items
                    _results.value = items
                    _displayedResults.value = items.take(PAGE_SIZE)
                    currentOffset = minOf(PAGE_SIZE, items.size)
                    _isLoading.value = false
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Search failed"
                    _isLoading.value = false
                }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value || _error.value != null) return
        if (currentOffset >= allResults.size) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            delay(300)
            val nextItems = allResults.drop(currentOffset).take(PAGE_SIZE)
            currentOffset += nextItems.size
            _displayedResults.value = _displayedResults.value + nextItems
            _isLoadingMore.value = false
        }
    }

    fun retry() {
        val currentQuery = _query.value
        if (currentQuery.isNotBlank()) {
            search(currentQuery)
        }
    }

    fun clearSearch() {
        suggestionJob?.cancel()
        _query.value = ""
        _results.value = emptyList()
        _displayedResults.value = emptyList()
        _suggestions.value = emptyList()
        _error.value = null
        allResults = emptyList()
        currentOffset = 0
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch {
            preferencesManager.removeSearchHistory(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            preferencesManager.clearSearchHistory()
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            repository.getTrending()
                .onSuccess { items ->
                    _trending.value = items
                    _isLoadingTrending.value = false
                }
                .onFailure {
                    _isLoadingTrending.value = false
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
}
