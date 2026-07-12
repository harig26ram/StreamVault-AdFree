package com.streamvault.app.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.AddToWatchLaterUseCase
import com.streamvault.app.domain.usecase.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

enum class SearchFilter(val label: String, val params: String?) {
    ALL("All", null),
    VIDEO("Videos", "EgIQAQ=="),
    CHANNEL("Channels", "EgIIAQ=="),
    PLAYLIST("Playlists", "EgIQBA=="),
    LAST_HOUR("Last hour", "EgIIBA=="),
    TODAY("Today", "EgIIAw=="),
    THIS_WEEK("This week", "EgIIAg=="),
    THIS_MONTH("This month", "EgIIAQ=="),
    THIS_YEAR("This year", "EgIIBQ=="),
    SHORT("Under 4 min", "EgIYAw=="),
    MEDIUM("4-20 min", "EgIYAg=="),
    LONG("Over 20 min", "EgIYAQ==")
}

data class SearchUiState(
    val query: String = "",
    val results: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val continuationToken: String? = null,
    val searchHistory: List<String> = emptyList(),
    val selectedFilter: SearchFilter = SearchFilter.ALL
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase,
    private val addToWatchLaterUseCase: AddToWatchLaterUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
    }

    init {
        _uiState.value = _uiState.value.copy(searchHistory = loadSearchHistory())
    }

    private fun loadSearchHistory(): List<String> {
        val json = prefs.getString("search_history", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSearchHistory(history: List<String>) {
        val array = JSONArray()
        history.forEach { array.put(it) }
        prefs.edit().putString("search_history", array.toString()).apply()
    }

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
        val params = _uiState.value.selectedFilter.params
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            searchUseCase(query, params = params).fold(
                onSuccess = { result ->
                    val newHistory = (_uiState.value.searchHistory + query).distinct().take(20)
                    _uiState.value = _uiState.value.copy(
                        results = result.items,
                        continuationToken = result.continuationToken,
                        isLoading = false,
                        searchHistory = newHistory
                    )
                    saveSearchHistory(newHistory)
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
        val params = _uiState.value.selectedFilter.params
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            searchUseCase(_uiState.value.query, token, params).fold(
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
        val newHistory = _uiState.value.searchHistory - query
        _uiState.value = _uiState.value.copy(searchHistory = newHistory)
        saveSearchHistory(newHistory)
    }

    fun refresh() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        val params = _uiState.value.selectedFilter.params
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            searchUseCase(query, params = params).fold(
                onSuccess = { result ->
                    val newHistory = (_uiState.value.searchHistory + query).distinct().take(20)
                    _uiState.value = _uiState.value.copy(
                        results = result.items,
                        continuationToken = result.continuationToken,
                        isRefreshing = false,
                        searchHistory = newHistory
                    )
                    saveSearchHistory(newHistory)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isRefreshing = false
                    )
                }
            )
        }
    }

    fun onFilterSelected(filter: SearchFilter) {
        if (_uiState.value.selectedFilter == filter) return
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        if (_uiState.value.query.isNotBlank()) {
            search()
        }
    }

    fun addToWatchLater(video: Video) {
        viewModelScope.launch {
            addToWatchLaterUseCase(video)
        }
    }
}