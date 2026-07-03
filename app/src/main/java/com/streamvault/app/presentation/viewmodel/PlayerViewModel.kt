package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.data.api.SponsorBlockApi
import com.streamvault.app.data.api.SponsorSegment
import com.streamvault.app.data.local.SettingsManager
import com.streamvault.app.domain.model.CaptionTrack
import com.streamvault.app.domain.model.Comment
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.model.VideoFormat
import com.streamvault.app.domain.usecase.AddToWatchHistoryUseCase
import com.streamvault.app.domain.usecase.GetCaptionTracksUseCase
import com.streamvault.app.domain.usecase.GetCommentsUseCase
import com.streamvault.app.domain.usecase.GetRelatedVideosUseCase
import com.streamvault.app.domain.usecase.GetVideoFormatsUseCase
import com.streamvault.app.domain.usecase.GetVideoInfoUseCase
import com.streamvault.app.domain.usecase.GetVideoStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val video: Video? = null,
    val videoUrl: String? = null,
    val relatedVideos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val currentTime: Long = 0,
    val duration: Long = 0,
    val volume: Float = 1f,
    val brightness: Float = 0.5f,
    val quality: String = "Auto",
    val formats: List<VideoFormat> = emptyList(),
    val selectedFormat: VideoFormat? = null,
    val captionTracks: List<CaptionTrack> = emptyList(),
    val sponsorSegments: List<SponsorSegment> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val isFullscreen: Boolean = false,
    val isBuffering: Boolean = false,
    val showUI: Boolean = true,
    val isLandscape: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVideoInfoUseCase: GetVideoInfoUseCase,
    private val getVideoStreamUseCase: GetVideoStreamUseCase,
    private val getVideoFormatsUseCase: GetVideoFormatsUseCase,
    private val getCaptionTracksUseCase: GetCaptionTracksUseCase,
    private val getCommentsUseCase: GetCommentsUseCase,
    private val sponsorBlockApi: SponsorBlockApi,
    private val settingsManager: SettingsManager,
    private val getRelatedVideosUseCase: GetRelatedVideosUseCase,
    private val addToWatchHistoryUseCase: AddToWatchHistoryUseCase
) : ViewModel() {

    val sponsorBlockEnabled: Boolean get() = settingsManager.sponsorBlock

    private val videoId: String = savedStateHandle["videoId"] ?: ""

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _currentVideoId = MutableStateFlow(videoId)
    val currentVideoId: StateFlow<String> = _currentVideoId.asStateFlow()

    private var loadVideoJob: Job? = null

    init {
        if (videoId.isNotBlank()) {
            loadVideo(videoId)
        }
    }

    fun loadVideo(id: String = videoId) {
        // Cancel any previous load to prevent race conditions
        loadVideoJob?.cancel()
        _currentVideoId.value = id

        loadVideoJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                videoUrl = null,
                video = null,
                relatedVideos = emptyList()
            )

            try {
                coroutineScope {
                    val infoDeferred = async { getVideoInfoUseCase(id) }
                    val streamDeferred = async { getVideoStreamUseCase(id) }
                    val relatedDeferred = async { getRelatedVideosUseCase(id) }

                    infoDeferred.await().fold(
                        onSuccess = { video ->
                            _uiState.value = _uiState.value.copy(video = video)
                            addToWatchHistoryUseCase(video)
                        },
                        onFailure = { e ->
                            _uiState.value = _uiState.value.copy(error = e.message)
                        }
                    )

                    streamDeferred.await().fold(
                        onSuccess = { url ->
                            _uiState.value = _uiState.value.copy(videoUrl = url, isLoading = false)
                        },
                        onFailure = { e ->
                            _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
                        }
                    )

                    relatedDeferred.await().fold(
                        onSuccess = { videos ->
                            _uiState.value = _uiState.value.copy(relatedVideos = videos)
                        },
                        onFailure = { }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load video: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun onPlayerStateChanged(isPlaying: Boolean, currentPosition: Long, duration: Long) {
        _uiState.value = _uiState.value.copy(
            isPlaying = isPlaying,
            currentTime = currentPosition,
            duration = duration
        )
    }

    fun onVolumeChanged(volume: Float) {
        _uiState.value = _uiState.value.copy(volume = volume.coerceIn(0f, 1f))
    }

    fun onBrightnessChanged(brightness: Float) {
        val b = brightness.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(brightness = b)
    }

    fun seekTo(position: Long) {}
    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(isFullscreen = !_uiState.value.isFullscreen)
    }

    fun playNextVideo() {
        val related = _uiState.value.relatedVideos
        val currentId = _currentVideoId.value
        val nextVideo = related.firstOrNull { it.id != currentId }
        if (nextVideo != null) {
            loadVideo(nextVideo.id)
        }
    }

    fun loadFormats(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            try {
                getVideoFormatsUseCase(videoId).fold(
                    onSuccess = { formats ->
                        _uiState.value = _uiState.value.copy(formats = formats)
                    },
                    onFailure = { }
                )
            } catch (_: Exception) { }
        }
    }

    fun setQuality(format: VideoFormat) {
        _uiState.value = _uiState.value.copy(
            videoUrl = format.url,
            selectedFormat = format,
            quality = if (format.height != null) "${format.height}p" else "Audio"
        )
    }

    fun setQualityAuto() {
        _currentVideoId.value.let { id ->
            viewModelScope.launch {
                try {
                    getVideoStreamUseCase(id).fold(
                        onSuccess = { url ->
                            _uiState.value = _uiState.value.copy(
                                videoUrl = url,
                                selectedFormat = null,
                                quality = "Auto"
                            )
                        },
                        onFailure = { }
                    )
                } catch (_: Exception) { }
            }
        }
    }

    fun loadCaptions(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            try {
                getCaptionTracksUseCase(videoId).fold(
                    onSuccess = { tracks ->
                        _uiState.value = _uiState.value.copy(captionTracks = tracks)
                    },
                    onFailure = { }
                )
            } catch (_: Exception) { }
        }
    }

    fun loadSponsorSegments(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            try {
                val response = sponsorBlockApi.getSkipSegments(videoId)
                val segments = response.skipSegments?.filter { it.actionType == "skip" } ?: emptyList()
                _uiState.value = _uiState.value.copy(sponsorSegments = segments)
            } catch (_: Exception) {
                // SponsorBlock segments not available for this video, ignore
            }
        }
    }

    fun loadComments(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            try {
                getCommentsUseCase(videoId).fold(
                    onSuccess = { comments ->
                        _uiState.value = _uiState.value.copy(comments = comments)
                    },
                    onFailure = { }
                )
            } catch (_: Exception) { }
        }
    }
}
