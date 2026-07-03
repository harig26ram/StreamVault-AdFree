package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.Playlist
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.GetPlaylistUseCase
import com.streamvault.app.domain.usecase.GetVideoStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val streamUrl: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistUseCase: GetPlaylistUseCase,
    private val getVideoStreamUseCase: GetVideoStreamUseCase
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"] ?: ""

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        if (playlistId.isNotEmpty()) {
            loadPlaylist()
        }
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            getPlaylistUseCase(playlistId).fold(
                onSuccess = { playlist ->
                    _uiState.value = _uiState.value.copy(
                        playlist = playlist,
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

    fun getStreamUrl(videoId: String) {
        viewModelScope.launch {
            getVideoStreamUseCase(videoId).fold(
                onSuccess = { url ->
                    _uiState.value = _uiState.value.copy(streamUrl = url)
                },
                onFailure = { }
            )
        }
    }
}
