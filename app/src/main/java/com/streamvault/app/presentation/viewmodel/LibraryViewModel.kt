package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.Playlist
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.GetHomeFeedUseCase
import com.streamvault.app.domain.usecase.GetWatchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val watchHistory: List<Video> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getWatchHistoryUseCase: GetWatchHistoryUseCase,
    private val getHomeFeedUseCase: GetHomeFeedUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadWatchHistory()
        loadPlaylists()
    }

    private fun loadWatchHistory() {
        viewModelScope.launch {
            getWatchHistoryUseCase().collect { history ->
                _uiState.value = _uiState.value.copy(watchHistory = history)
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            getHomeFeedUseCase().fold(
                onSuccess = { feed ->
                    val playlists = feed.items.filterIsInstance<FeedItem.Playlist>()
                        .map { it.playlist }
                    _uiState.value = _uiState.value.copy(
                        playlists = playlists,
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
}
