package com.freedomplay.app.presentation.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.db.DownloadDao
import com.freedomplay.app.data.local.db.DownloadEntity
import com.freedomplay.app.data.local.db.PlaylistDao
import com.freedomplay.app.data.local.db.PlaylistEntity
import com.freedomplay.app.data.local.db.PlaylistWithCount
import com.freedomplay.app.data.local.db.WatchHistoryDao
import com.freedomplay.app.data.local.db.WatchHistoryEntity
import com.freedomplay.app.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val playlists: List<PlaylistWithCount> = emptyList(),
    val watchHistory: List<WatchHistoryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val downloadDao: DownloadDao,
    private val playlistDao: PlaylistDao,
    private val watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
        loadPlaylists()
        loadWatchHistory()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                downloadManager.getDownloads().collect { downloads ->
                    _uiState.update { it.copy(downloads = downloads, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load downloads") }
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                playlistDao.getPlaylistsWithCount().collect { playlists ->
                    _uiState.update { it.copy(playlists = playlists) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to load playlists") }
            }
        }
    }

    private fun loadWatchHistory() {
        viewModelScope.launch {
            try {
                watchHistoryDao.getAll().collect { history ->
                    _uiState.update { it.copy(watchHistory = history) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to load watch history") }
            }
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                downloadManager.deleteDownload(download)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete download") }
            }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            try {
                val downloads = _uiState.value.downloads.filter { it.downloadStatus == "COMPLETED" }
                downloads.forEach { downloadManager.deleteDownload(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to clear downloads") }
            }
        }
    }

    fun pauseDownload(videoId: String) {
        viewModelScope.launch {
            try {
                downloadManager.pauseDownload(videoId)
                downloadDao.getDownload(videoId)?.let { download ->
                    downloadDao.update(download.copy(downloadStatus = "PAUSED"))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to pause download") }
            }
        }
    }

    fun resumeDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                downloadManager.startDownload(
                    videoId = download.videoId,
                    title = download.title,
                    channelName = download.channelName,
                    thumbnailUrl = download.thumbnailUrl,
                    quality = download.quality
                )
                downloadDao.update(download.copy(downloadStatus = "DOWNLOADING", progress = 0))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to resume download") }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistDao.insertPlaylist(PlaylistEntity(name = name))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create playlist") }
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistWithCount) {
        viewModelScope.launch {
            try {
                playlistDao.deletePlaylistById(playlist.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete playlist") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
