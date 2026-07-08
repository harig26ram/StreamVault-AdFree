package com.streamvault.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.data.download.DownloadManager
import com.streamvault.app.data.local.DownloadEntity
import com.streamvault.app.data.local.VideoDao
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.Playlist
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.ClearWatchLaterUseCase
import com.streamvault.app.domain.usecase.GetHomeFeedUseCase
import com.streamvault.app.domain.usecase.GetWatchHistoryUseCase
import com.streamvault.app.domain.usecase.GetWatchLaterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val watchHistory: List<Video> = emptyList(),
    val watchLater: List<Video> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getWatchHistoryUseCase: GetWatchHistoryUseCase,
    private val getWatchLaterUseCase: GetWatchLaterUseCase,
    private val clearWatchLaterUseCase: ClearWatchLaterUseCase,
    private val getHomeFeedUseCase: GetHomeFeedUseCase,
    private val videoDao: VideoDao,
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadWatchHistory()
        loadWatchLater()
        loadPlaylists()
        loadDownloads()
    }

    private fun loadWatchHistory() {
        viewModelScope.launch {
            getWatchHistoryUseCase().collect { history ->
                _uiState.value = _uiState.value.copy(watchHistory = history)
            }
        }
    }

    private fun loadWatchLater() {
        viewModelScope.launch {
            getWatchLaterUseCase().collect { watchLater ->
                _uiState.value = _uiState.value.copy(watchLater = watchLater)
            }
        }
    }

    fun clearWatchLater() {
        viewModelScope.launch {
            clearWatchLaterUseCase()
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

    private fun loadDownloads() {
        viewModelScope.launch {
            videoDao.getAllDownloads().collect { downloads ->
                _uiState.value = _uiState.value.copy(downloads = downloads)
            }
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            val entity = videoDao.getDownload(videoId) ?: return@launch
            if (entity.filePath.isNotEmpty()) {
                try {
                    context.filesDir.resolve(entity.filePath).delete()
                } catch (_: Exception) {}
            }
            videoDao.deleteDownload(videoId)
        }
    }

    fun pauseDownload(videoId: String) {
        downloadManager.pauseDownload(videoId)
    }
}
