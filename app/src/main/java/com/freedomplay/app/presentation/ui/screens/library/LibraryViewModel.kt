package com.freedomplay.app.presentation.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.db.DownloadEntity
import com.freedomplay.app.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                downloadManager.getDownloads().collect { downloads ->
                    _uiState.value = LibraryUiState(
                        downloads = downloads,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LibraryUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load downloads"
                )
            }
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            try {
                downloadManager.deleteDownload(download)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to delete download")
            }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            try {
                val downloads = _uiState.value.downloads.filter { it.downloadStatus == "COMPLETED" }
                downloads.forEach { downloadManager.deleteDownload(it) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to clear downloads")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
