package com.freedomplay.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.preferences.PreferencesManager
import com.freedomplay.app.data.repository.StreamRepository
import com.freedomplay.app.domain.model.Stream
import com.freedomplay.app.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: StreamRepository,
    private val downloadManager: DownloadManager,
    private val preferencesManager: PreferencesManager,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _stream = MutableStateFlow<Stream?>(null)
    val stream: StateFlow<Stream?> = _stream.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedQuality = MutableStateFlow("Auto")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentVideoId = MutableStateFlow<String?>(null)

    val defaultQuality = preferencesManager.defaultQuality
        .catch { emit("720p") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "720p")

    val skipSilence = preferencesManager.skipSilence
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val audioOnly = preferencesManager.audioOnlyMode
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rememberPosition = preferencesManager.rememberPosition
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun loadVideo(videoId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _currentVideoId.value = videoId

            val isFav = preferencesManager.favorites.first().contains(videoId)
            _isFavorite.value = isFav

            repository.getStreams(videoId)
                .onSuccess { s ->
                    _stream.value = s
                    val prefQuality = preferencesManager.defaultQuality.first()
                    if (prefQuality != "Auto") {
                        val matched = s.videoStreams
                            .filter { it.quality?.contains(prefQuality.removeSuffix("p"), ignoreCase = true) == true && it.url != null }
                            .maxByOrNull { it.height ?: 0 }
                        if (matched != null) {
                            _selectedQuality.value = matched.quality ?: "Auto"
                        }
                    }
                    _isLoading.value = false
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load video"
                    _isLoading.value = false
                }
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun selectQuality(quality: String) {
        _selectedQuality.value = quality
    }

    fun toggleFavorite() {
        val videoId = _currentVideoId.value ?: return
        viewModelScope.launch {
            val newState = !_isFavorite.value
            _isFavorite.value = newState
            preferencesManager.setFavorite(videoId, newState)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun savePosition(videoId: String, positionMs: Long) {
        viewModelScope.launch {
            preferencesManager.savePosition(videoId, positionMs)
        }
    }

    suspend fun getSavedPosition(videoId: String): Long {
        return if (preferencesManager.rememberPosition.first()) {
            preferencesManager.getSavedPosition(videoId)
        } else 0L
    }

    fun startDownload(
        @Suppress("UNUSED_PARAMETER") context: Context,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String
    ) {
        downloadManager.startDownload(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            quality = _selectedQuality.value
        )
    }
}
