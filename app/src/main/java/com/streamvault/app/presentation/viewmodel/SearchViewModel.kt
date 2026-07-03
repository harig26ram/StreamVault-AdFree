package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.SearchResult
import com.streamvault.app.domain.usecase.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val continuationToken: String? = null,
    val searchHistory: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(300) // Debounce
                search(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(results = emptyList())
        }
    }

    fun search(query: String = _uiState.value.query) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            searchUseCase(query).fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        results = result.items,
                        continuationToken = result.continuationToken,
                        isLoading = false,
                        searchHistory = (_uiState.value.searchHistory + query).distinct().take(20)
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            )
        }
    }

    fun loadMore() {
        val token = _uiState.value.continuationToken ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            searchUseCase(_uiState.value.query, token).fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results + result.items,
                        continuationToken = result.continuationToken,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            )
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            query = "",
            results = emptyList(),
            continuationToken = null,
            error = null
        )
    }

    fun removeFromHistory(query: String) {
        _uiState.value = _uiState.value.copy(
            searchHistory = _uiState.value.searchHistory - query
        )
    }
}