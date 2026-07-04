package com.streamvault.app.presentation.viewmodel

import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.streamvault.player.core.PlayerConfig
import com.streamvault.player.core.PlayerEngine
import com.streamvault.player.core.PlayerState
import com.streamvault.player.sponsorblock.SponsorBlockManager
import com.streamvault.player.sponsorblock.data.SponsorSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val video: Video? = null,
    val relatedVideos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val volume: Float = 1f,
    val brightness: Float = 0.5f,
    val qualityLabel: String = "Auto",
    val captionTracks: List<CaptionTrack> = emptyList(),
    val selectedCaption: CaptionTrack? = null,
    val comments: List<Comment> = emptyList(),
    val isFullscreen: Boolean = false,
    val isLandscape: Boolean = false,
    val playerState: PlayerState = PlayerState.Idle,
    val position: Long = 0,
    val duration: Long = 0,
    val bufferedPercent: Int = 0,
    val playbackSpeed: Float = 1f,
    val formats: List<VideoFormat> = emptyList(),
    val selectedFormat: VideoFormat? = null,
    val sponsorSegments: List<SponsorSegment> = emptyList(),
    val isAudioOnly: Boolean = false,
    val showVolumeIndicator: Boolean = false,
    val showBrightnessIndicator: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVideoInfoUseCase: GetVideoInfoUseCase,
    private val getVideoFormatsUseCase: GetVideoFormatsUseCase,
    private val getCaptionTracksUseCase: GetCaptionTracksUseCase,
    private val getCommentsUseCase: GetCommentsUseCase,
    private val settingsManager: SettingsManager,
    private val getRelatedVideosUseCase: GetRelatedVideosUseCase,
    private val addToWatchHistoryUseCase: AddToWatchHistoryUseCase
) : ViewModel() {

    val engine = PlayerEngine(PlayerConfig())
    private val sponsorBlockManager = SponsorBlockManager()

    val sponsorBlockEnabled: Boolean get() = settingsManager.sponsorBlock

    private val videoId: String = savedStateHandle["videoId"] ?: ""

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _currentVideoId = MutableStateFlow(videoId)
    val currentVideoId: StateFlow<String> = _currentVideoId.asStateFlow()

    private var loadVideoJob: Job? = null
    private var sponsorCheckJob: Job? = null

    init {
        collectEngineState()
        if (videoId.isNotBlank()) loadVideo(videoId)
    }

    private fun collectEngineState() {
        viewModelScope.launch {
            engine.state.collect { state ->
                _uiState.update { it.copy(playerState = state) }
            }
        }
        viewModelScope.launch {
            engine.position.collect { pos ->
                _uiState.update { it.copy(position = pos) }
            }
        }
        viewModelScope.launch {
            engine.duration.collect { dur ->
                _uiState.update { it.copy(duration = dur) }
            }
        }
        viewModelScope.launch {
            engine.bufferedPercent.collect { pct ->
                _uiState.update { it.copy(bufferedPercent = pct) }
            }
        }
    }

    fun setSurface(surface: Surface?) {
        surface?.let { engine.setSurface(it) }
    }

    fun loadVideo(id: String = videoId) {
        loadVideoJob?.cancel()
        engine.stop()
        _currentVideoId.value = id

        loadVideoJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    video = null,
                    relatedVideos = emptyList(),
                    playerState = PlayerState.Idle,
                    position = 0,
                    duration = 0,
                    isAudioOnly = false
                )
            }

            try {
                coroutineScope {
                    val infoDeferred = async { getVideoInfoUseCase(id) }
                    val relatedDeferred = async { getRelatedVideosUseCase(id) }

                    infoDeferred.await().fold(
                        onSuccess = { video ->
                            _uiState.update { it.copy(video = video) }
                            addToWatchHistoryUseCase(video)
                        },
                        onFailure = { e ->
                            _uiState.update { it.copy(error = e.message) }
                        }
                    )

                    relatedDeferred.await().fold(
                        onSuccess = { videos ->
                            _uiState.update { it.copy(relatedVideos = videos) }
                        },
                        onFailure = { }
                    )
                }

                loadFormats(id)
                loadCaptions(id)
                loadComments(id)

                if (sponsorBlockEnabled) {
                    loadSponsorSegments(id)
                    startSponsorCheck()
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to load video: ${e.message}", isLoading = false)
                }
            }
        }
    }

    fun loadFormats(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            getVideoFormatsUseCase(videoId).fold(
                onSuccess = { formats ->
                    _uiState.update { it.copy(formats = formats) }
                    loadBestStream(formats)
                },
                onFailure = { }
            )
        }
    }

    private fun loadBestStream(formats: List<VideoFormat>) {
        if (formats.isEmpty()) return

        val progressive = formats.firstOrNull { !it.isAdaptive && it.isVideo }
        if (progressive != null) {
            engine.loadStreams(progressive.url, progressive.url, progressive.url)
            _uiState.update {
                it.copy(
                    selectedFormat = progressive,
                    qualityLabel = if (progressive.height != null) "${progressive.height}p" else "Auto"
                )
            }
            return
        }

        val bestVideo = formats.filter { it.isAdaptive && it.isVideo }
            .maxByOrNull { it.height ?: 0 }
        val bestAudio = formats.filter { it.isAdaptive && it.isAudio }
            .maxByOrNull { it.bitrate }

        if (bestVideo != null && bestAudio != null) {
            engine.loadStreams(bestAudio.url, bestVideo.url)
            _uiState.update {
                it.copy(
                    selectedFormat = bestVideo,
                    qualityLabel = if (bestVideo.height != null) "${bestVideo.height}p" else "Auto"
                )
            }
        } else if (bestVideo != null) {
            engine.loadStreams(bestVideo.url, bestVideo.url, bestVideo.url)
        } else if (bestAudio != null) {
            engine.loadStreams(bestAudio.url, bestAudio.url, bestAudio.url)
            _uiState.update { it.copy(isAudioOnly = true) }
        }
    }

    fun play() {
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun togglePlayPause() {
        val state = _uiState.value.playerState
        if (state == PlayerState.Playing) engine.pause()
        else engine.play()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    fun playNextVideo() {
        val related = _uiState.value.relatedVideos
        val currentId = _currentVideoId.value
        val nextVideo = related.firstOrNull { it.id != currentId }
        if (nextVideo != null) {
            loadVideo(nextVideo.id)
        }
    }

    fun setQuality(format: VideoFormat) {
        _uiState.update {
            it.copy(
                selectedFormat = format,
                qualityLabel = if (format.height != null) "${format.height}p" else "Audio"
            )
        }

        val isProgressive = !format.isAdaptive
        val isAudioOnly = format.isAdaptive && format.isAudio

        if (isProgressive) {
            engine.loadStreams(format.url, format.url, format.url)
        } else if (isAudioOnly) {
            engine.loadStreams(format.url, format.url, format.url)
            _uiState.update { it.copy(isAudioOnly = true) }
        } else {
            val bestAudio = _uiState.value.formats
                .filter { it.isAdaptive && it.isAudio }
                .maxByOrNull { it.bitrate }
            if (bestAudio != null) {
                engine.loadStreams(bestAudio.url, format.url)
            } else {
                engine.loadStreams(format.url, format.url, format.url)
            }
        }
    }

    fun setQualityAuto() {
        _currentVideoId.value.let { id ->
            viewModelScope.launch {
                getVideoFormatsUseCase(id).fold(
                    onSuccess = { formats ->
                        _uiState.update { it.copy(formats = formats, selectedFormat = null, qualityLabel = "Auto") }
                        loadBestStream(formats)
                    },
                    onFailure = { }
                )
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun onVolumeChanged(volume: Float) {
        _uiState.update {
            it.copy(volume = volume.coerceIn(0f, 1f), showVolumeIndicator = true)
        }
    }

    fun onBrightnessChanged(brightness: Float) {
        _uiState.update {
            it.copy(brightness = brightness.coerceIn(0f, 1f), showBrightnessIndicator = true)
        }
    }

    fun hideVolumeIndicator() {
        _uiState.update { it.copy(showVolumeIndicator = false) }
    }

    fun hideBrightnessIndicator() {
        _uiState.update { it.copy(showBrightnessIndicator = false) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun loadCaptions(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            getCaptionTracksUseCase(videoId).fold(
                onSuccess = { tracks ->
                    _uiState.update { it.copy(captionTracks = tracks) }
                },
                onFailure = { }
            )
        }
    }

    fun loadSponsorSegments(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            sponsorBlockManager.loadSegments(videoId).onSuccess { segments ->
                _uiState.update { it.copy(sponsorSegments = segments) }
            }
        }
    }

    fun loadComments(videoId: String = _currentVideoId.value) {
        viewModelScope.launch {
            getCommentsUseCase(videoId).fold(
                onSuccess = { comments ->
                    _uiState.update { it.copy(comments = comments) }
                },
                onFailure = { }
            )
        }
    }

    private fun startSponsorCheck() {
        sponsorCheckJob?.cancel()
        sponsorCheckJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value.playerState
                val pos = _uiState.value.position
                val action = sponsorBlockManager.checkSegments(state, pos)
                if (action is com.streamvault.player.sponsorblock.SponsorBlockAction.Skip) {
                    val endMs = (action.segment.segment[1] * 1000).toLong()
                    engine.seekTo(endMs)
                }
                delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
