package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicCategory(
    val name: String,
    val query: String,
    val icon: String
)

data class MusicUiState(
    val searchQuery: String = "",
    val musicResults: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCategory: MusicCategory? = null,
    val error: String? = null
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    val categories = listOf(
        MusicCategory("Top Charts", "top charts music 2026", "🎵"),
        MusicCategory("New Releases", "new music releases 2026", "🆕"),
        MusicCategory("Trending", "trending music now", "🔥"),
        MusicCategory("Podcasts", "music podcasts", "🎙️"),
        MusicCategory("Live", "live music performance", "📺"),
        MusicCategory("Mixes", "music mix playlist", "🎶")
    )

    init {
        searchCategory(categories[2]) // Load trending by default
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(400)
                searchMusic(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(musicResults = emptyList())
        }
    }

    fun searchMusic(query: String = _uiState.value.searchQuery) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            searchUseCase(query).fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        musicResults = result.items,
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

    fun searchCategory(category: MusicCategory) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            searchQuery = ""
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            searchUseCase(category.query).fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        musicResults = result.items,
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
            searchQuery = "",
            musicResults = emptyList(),
            error = null
        )
    }
}
