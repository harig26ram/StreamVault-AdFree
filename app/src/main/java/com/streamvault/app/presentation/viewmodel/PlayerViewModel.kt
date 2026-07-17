package com.streamvault.app.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.data.local.SettingsManager
import com.streamvault.app.data.local.VideoDao
import com.streamvault.app.data.local.WatchHistoryEntity
import com.streamvault.app.data.local.WatchLaterEntity
import com.streamvault.app.data.download.DownloadManager
import com.streamvault.app.domain.model.CaptionTrack
import com.streamvault.app.domain.model.Chapter
import com.streamvault.app.domain.model.Comment
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.model.VideoFormat
import com.streamvault.app.domain.usecase.AddToWatchHistoryUseCase
import com.streamvault.app.domain.usecase.GetCaptionTracksUseCase
import com.streamvault.app.domain.usecase.GetCommentsUseCase
import com.streamvault.app.domain.usecase.GetRelatedVideosUseCase
import com.streamvault.app.domain.usecase.GetVideoFormatsUseCase
import com.streamvault.app.domain.usecase.GetVideoInfoUseCase
import com.streamvault.app.domain.usecase.SubscribeUseCase
import com.streamvault.app.domain.usecase.UnsubscribeUseCase
import com.streamvault.app.presentation.ui.components.MiniPlayerManager
import com.streamvault.app.service.PlaybackService
import com.streamvault.player.core.EqualizerManager
import com.streamvault.player.core.PlayerConfig
import com.streamvault.player.core.PlayerEngine
import com.streamvault.player.core.PlayerState
import com.streamvault.player.sponsorblock.SponsorBlockManager
import com.streamvault.player.sponsorblock.data.SponsorSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

data class QueueItem(
    val videoId: String,
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = ""
)

enum class RepeatMode { OFF, ONE, ALL }

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
    val showBrightnessIndicator: Boolean = false,
    val isSubscribed: Boolean = false,
    val savedToWatchLater: Boolean = false,
    val isPipMode: Boolean = false,
    val showResumeDialog: Boolean = false,
    val savedPositionMs: Long = 0,
    val isMiniPlayer: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val showQueueSheet: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val isMiniPlayerEnabled: Boolean = true,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val downloadProgress: Int = 0,
    val isPlayingOffline: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val isRecovering: Boolean = false,
    val recoveryMessage: String? = null
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
    private val addToWatchHistoryUseCase: AddToWatchHistoryUseCase,
    private val subscribeUseCase: SubscribeUseCase,
    private val unsubscribeUseCase: UnsubscribeUseCase,
    private val videoDao: VideoDao,
    private val downloadManager: DownloadManager,
    private val miniPlayerManager: MiniPlayerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val engineConfig = PlayerConfig(
        skipSilenceEnabled = settingsManager.skipSilence,
        equalizerEnabled = settingsManager.equalizerEnabled
    )
    val engine = PlayerEngine(engineConfig, context)
    private val sponsorBlockManager = SponsorBlockManager()
    private val recoveryManager = PlaybackRecoveryManager()

    val sponsorBlockEnabled: Boolean get() = settingsManager.sponsorBlock

    private val videoId: String = savedStateHandle["videoId"] ?: ""

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _currentVideoId = MutableStateFlow(videoId)
    val currentVideoId: StateFlow<String> = _currentVideoId.asStateFlow()

    private var loadVideoJob: Job? = null
    private var sponsorCheckJob: Job? = null
    private var positionSaveJob: Job? = null
    private var surfaceReady = false
    private var pendingAudioUrl: String? = null
    private var pendingVideoUrl: String? = null
    private var pendingProgressiveUrl: String? = null

    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            try {
                val state = _uiState.value
                val playing = state.playerState == PlayerState.Playing ||
                    state.playerState == PlayerState.Buffering
                if (playing && settingsManager.backgroundPlay &&
                    !state.isPipMode && !miniPlayerManager.state.value.isActive
                ) {
                    startBackgroundService()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lifecycle ON_STOP handler error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PlayerVM"
        private val CHAPTER_REGEX = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?\s+(.+)$""", RegexOption.MULTILINE)

        fun parseChaptersFromDescription(description: String): List<Chapter> {
            if (description.isBlank()) return emptyList()
            return CHAPTER_REGEX.findAll(description).mapNotNull { match ->
                val groups = match.groupValues
                val h = if (groups[3].isNotEmpty()) groups[3].toLongOrNull() ?: 0L else 0L
                val m = groups[1].toLongOrNull() ?: return@mapNotNull null
                val s = groups[2].toLongOrNull() ?: return@mapNotNull null
                val title = groups[4].trim()
                if (title.isEmpty()) return@mapNotNull null
                Chapter(
                    title = title,
                    startTimeMs = (h * 3600 + m * 60 + s) * 1000
                )
            }.toList()
        }
    }

    init {
        _uiState.update { it.copy(isMiniPlayerEnabled = settingsManager.miniPlayer) }
        collectEngineState()
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register lifecycle observer: ${e.message}")
        }
        Log.d(TAG, "init: videoId=$videoId")
        if (videoId.isNotBlank()) {
            loadVideo(videoId)
            checkDownloadStatus()
        }
    }

    private fun collectEngineState() {
        viewModelScope.launch {
            engine.state.collect { state ->
                _uiState.update { it.copy(playerState = state) }

                if (state is PlayerState.Error) {
                    handlePlaybackError(state)
                } else if (state == PlayerState.Playing || state == PlayerState.Buffering) {
                    recoveryManager.reset()
                    _uiState.update { it.copy(isRecovering = false, recoveryMessage = null) }
                }
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
        if (surface == null) {
            Log.d(TAG, "setSurface: null surface received")
            engine.setSurface(null)
            surfaceReady = false
            pendingProgressiveUrl = null
            pendingAudioUrl = null
            pendingVideoUrl = null
            return
        }
        Log.d(TAG, "setSurface: surface set, surfaceReady=$surfaceReady")
        engine.setSurface(surface)
        surfaceReady = true
        val p = pendingProgressiveUrl
        val a = pendingAudioUrl
        val v = pendingVideoUrl
        if (p != null) {
            pendingProgressiveUrl = null
            engine.loadStreams(null, null, p)
            autoPlay()
        } else if (a != null || v != null) {
            pendingAudioUrl = null
            pendingVideoUrl = null
            engine.loadStreams(a, v)
            autoPlay()
        }
    }

    fun loadVideo(id: String = videoId) {
        Log.d(TAG, "loadVideo: id=$id")
        loadVideoJob?.cancel()
        engine.stop()
        _currentVideoId.value = id
        recoveryManager.reset()

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
                    isAudioOnly = false,
                    isRecovering = false,
                    recoveryMessage = null
                )
            }

            try {
                coroutineScope {
                    val infoDeferred = async { getVideoInfoUseCase(id) }
                    val relatedDeferred = async { getRelatedVideosUseCase(id) }

                    infoDeferred.await().fold(
                        onSuccess = { video ->
                            Log.d(TAG, "loadVideo: info success, title=${video.title}")
                            val chapters = parseChaptersFromDescription(video.description)
                            _uiState.update { it.copy(video = video, chapters = chapters) }
                            addToWatchHistoryUseCase(video)
                            checkSavedPosition(id)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "loadVideo: info FAILED: ${e.message}", e)
                            _uiState.update { it.copy(error = e.message) }
                        }
                    )

                    relatedDeferred.await().fold(
                        onSuccess = { videos ->
                            Log.d(TAG, "loadVideo: related success, count=${videos.size}")
                            _uiState.update { it.copy(relatedVideos = videos) }
                            addToQueueFromRelated(videos)
                        },
                        onFailure = { e ->
                            Log.w(TAG, "loadVideo: related FAILED: ${e.message}")
                        }
                    )
                }

                val isDownloaded = downloadManager.isDownloaded(id)
                if (isDownloaded) {
                    playOffline()
                } else {
                    loadFormats(id)
                }
                loadCaptions(id)
                loadComments(id)

                if (sponsorBlockEnabled) {
                    loadSponsorSegments(id)
                    startSponsorCheck()
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "loadVideo: exception", e)
                _uiState.update {
                    it.copy(error = "Failed to load video: ${e.message}", isLoading = false)
                }
            }
        }
    }

    private suspend fun checkSavedPosition(videoId: String) {
        if (!settingsManager.rememberPlayback) return
        val savedPos = videoDao.getSavedPosition(videoId) ?: return
        val duration = engine.duration.value
        if (savedPos > 3000L && (duration <= 0 || savedPos < duration - 3000L)) {
            _uiState.update { it.copy(showResumeDialog = true, savedPositionMs = savedPos) }
        }
    }

    fun resumeFromSavedPosition() {
        val savedPos = _uiState.value.savedPositionMs
        _uiState.update { it.copy(showResumeDialog = false) }
        engine.seekTo(savedPos)
        startBackgroundService()
    }

    fun dismissResumeDialog() {
        _uiState.update { it.copy(showResumeDialog = false) }
        startBackgroundService()
    }

    private fun addToQueueFromRelated(videos: List<Video>) {
        if (_uiState.value.queue.isNotEmpty()) return
        val queueItems = videos.filter { it.id.isNotBlank() }.map {
            QueueItem(
                videoId = it.id,
                title = it.title,
                channelName = it.channelName,
                thumbnailUrl = it.thumbnailUrl
            )
        }
        val currentVideo = _uiState.value.video
        val allItems = if (currentVideo != null) {
            listOf(
                QueueItem(
                    videoId = currentVideo.id,
                    title = currentVideo.title,
                    channelName = currentVideo.channelName,
                    thumbnailUrl = currentVideo.thumbnailUrl
                )
            ) + queueItems.filter { it.videoId != currentVideo.id }
        } else queueItems
        _uiState.update { it.copy(queue = allItems, queueIndex = 0) }
    }

    fun addToQueue(video: Video) {
        val item = QueueItem(
            videoId = video.id,
            title = video.title,
            channelName = video.channelName,
            thumbnailUrl = video.thumbnailUrl
        )
        _uiState.update { state ->
            state.copy(queue = state.queue + item)
        }
    }

    fun removeFromQueue(index: Int) {
        _uiState.update { state ->
            val newQueue = state.queue.toMutableList()
            if (index in newQueue.indices) {
                newQueue.removeAt(index)
                val newIndex = if (index < state.queueIndex) state.queueIndex - 1 else state.queueIndex
                state.copy(queue = newQueue, queueIndex = newIndex.coerceIn(-1, newQueue.size - 1))
            } else state
        }
    }

    fun clearQueue() {
        _uiState.update { it.copy(queue = emptyList(), queueIndex = -1) }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val newQueue = state.queue.toMutableList()
            if (fromIndex in newQueue.indices && toIndex in newQueue.indices) {
                val item = newQueue.removeAt(fromIndex)
                newQueue.add(toIndex, item)
                val newCurrentIndex = when {
                    state.queueIndex == fromIndex -> toIndex
                    fromIndex < state.queueIndex && toIndex >= state.queueIndex -> state.queueIndex - 1
                    fromIndex > state.queueIndex && toIndex <= state.queueIndex -> state.queueIndex + 1
                    else -> state.queueIndex
                }
                state.copy(queue = newQueue, queueIndex = newCurrentIndex)
            } else state
        }
    }

    fun toggleShuffle() {
        _uiState.update { state ->
            val newQueue = if (!state.shuffleEnabled) {
                state.queue.toMutableList().shuffled()
            } else {
                state.queue
            }
            state.copy(
                shuffleEnabled = !state.shuffleEnabled,
                queue = newQueue,
                queueIndex = newQueue.indexOfFirst { it.videoId == _currentVideoId.value }.coerceAtLeast(0)
            )
        }
    }

    fun cycleRepeatMode() {
        _uiState.update { state ->
            val nextMode = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.OFF
            }
            state.copy(repeatMode = nextMode)
        }
    }

    fun playFromQueue(index: Int) {
        val queue = _uiState.value.queue
        if (index in queue.indices) {
            _uiState.update { it.copy(queueIndex = index) }
            loadVideo(queue[index].videoId)
        }
    }

    fun toggleQueueSheet() {
        _uiState.update { it.copy(showQueueSheet = !it.showQueueSheet) }
    }

    fun loadFormats(videoId: String = _currentVideoId.value) {
        Log.d(TAG, "loadFormats: videoId=$videoId")
        viewModelScope.launch {
            getVideoFormatsUseCase(videoId).fold(
                onSuccess = { formats ->
                    Log.d(TAG, "loadFormats: success, count=${formats.size}")
                    _uiState.update { it.copy(formats = formats) }
                    loadBestStream(formats)
                },
                onFailure = { e ->
                    Log.e(TAG, "loadFormats: FAILED: ${e.message}", e)
                    _uiState.update { it.copy(error = "Failed to load video formats: ${e.message}", isLoading = false) }
                }
            )
        }
    }

    private fun loadBestStream(formats: List<VideoFormat>) {
        Log.d(TAG, "loadBestStream: formats.size=${formats.size}, surfaceReady=$surfaceReady")
        if (formats.isEmpty()) {
            _uiState.update { it.copy(error = "No playable streams found. Tap to retry.", isLoading = false) }
            return
        }

        val qualitySetting = settingsManager.videoQuality
        val targetHeight: Int? = when (qualitySetting) {
            "Auto", "Highest" -> null
            else -> qualitySetting.removeSuffix("p").toIntOrNull()
        }

        val videoFormats = formats.filter { it.isAdaptive && it.isVideo }
        val bestVideo = if (targetHeight == null) {
            videoFormats.maxByOrNull { it.height ?: 0 }
        } else {
            videoFormats
                .filter { (it.height ?: 0) >= targetHeight }
                .minByOrNull { it.height ?: 0 }
                ?: videoFormats.maxByOrNull { it.height ?: 0 }
        }
        val bestAudio = formats.filter { it.isAdaptive && it.isAudio }
            .maxByOrNull { it.bitrate }

        if (bestVideo != null && bestAudio != null) {
            _uiState.update {
                it.copy(
                    selectedFormat = bestVideo,
                    qualityLabel = if (bestVideo.height != null) "${bestVideo.height}p" else "Auto"
                )
            }
            if (surfaceReady) {
                engine.loadStreams(bestAudio.url, bestVideo.url)
                autoPlay()
            } else {
                pendingAudioUrl = bestAudio.url
                pendingVideoUrl = bestVideo.url
            }
        } else if (bestVideo != null) {
            _uiState.update {
                it.copy(
                    selectedFormat = bestVideo,
                    qualityLabel = if (bestVideo.height != null) "${bestVideo.height}p" else "Auto"
                )
            }
            if (surfaceReady) {
                engine.loadStreams(null, bestVideo.url)
                autoPlay()
            } else {
                pendingAudioUrl = null
                pendingVideoUrl = bestVideo.url
            }
        } else if (bestAudio != null) {
            if (surfaceReady) {
                engine.loadStreams(bestAudio.url, null)
                autoPlay()
            } else {
                pendingAudioUrl = bestAudio.url
                pendingVideoUrl = null
            }
            _uiState.update { it.copy(isAudioOnly = true) }
        } else {
            val progressive = formats.firstOrNull { !it.isAdaptive && it.isVideo }
                ?: formats.firstOrNull { !it.isAdaptive }
            if (progressive != null) {
                _uiState.update { it.copy(selectedFormat = progressive, qualityLabel = progressive.qualityLabel) }
                if (surfaceReady) {
                    engine.loadStreams(null, null, progressive.url)
                    autoPlay()
                } else {
                    pendingProgressiveUrl = progressive.url
                }
            }
        }
    }

    private fun handlePlaybackError(error: PlayerState.Error) {
        Log.d(TAG, "handlePlaybackError: ${error.message}")
        val action = recoveryManager.handleError(error)

        when (action) {
            PlaybackRecoveryManager.RecoveryAction.RETRY_SAME_URL -> {
                recoveryManager.scheduleRetry {
                    val videoId = _currentVideoId.value
                    val formats = _uiState.value.formats
                    if (formats.isNotEmpty()) {
                        loadBestStream(formats)
                    } else {
                        loadFormats(videoId)
                    }
                }
            }
            PlaybackRecoveryManager.RecoveryAction.REFETCH_FORMATS -> {
                recoveryManager.scheduleRetry {
                    loadFormats(_currentVideoId.value)
                }
            }
            PlaybackRecoveryManager.RecoveryAction.ROTATE_CLIENT -> {
                recoveryManager.scheduleRetry {
                    loadFormats(_currentVideoId.value)
                }
            }
            PlaybackRecoveryManager.RecoveryAction.DEGRADE_QUALITY -> {
                recoveryManager.scheduleRetry {
                    val formats = _uiState.value.formats
                    if (formats.isNotEmpty()) {
                        val videoFormats = formats.filter { it.isAdaptive && it.isVideo }
                        val lowerQuality = videoFormats
                            .filter { (it.height ?: 0) <= 480 }
                            .maxByOrNull { it.height ?: 0 }
                        if (lowerQuality != null) {
                            val audio = formats.filter { it.isAdaptive && it.isAudio }
                                .maxByOrNull { it.bitrate }
                            if (audio != null) {
                                engine.loadStreams(audio.url, lowerQuality.url)
                                autoPlay()
                            }
                        } else {
                            loadFormats(_currentVideoId.value)
                        }
                    }
                }
            }
            PlaybackRecoveryManager.RecoveryAction.SHOW_ERROR -> {
                _uiState.update {
                    it.copy(
                        error = error.message,
                        isRecovering = false,
                        recoveryMessage = null
                    )
                }
            }
        }

        _uiState.update {
            it.copy(
                isRecovering = recoveryManager.state.value.isRecovering,
                recoveryMessage = recoveryManager.state.value.recoveryMessage
            )
        }
    }

    fun retryPlayback() {
        recoveryManager.reset()
        _uiState.update { it.copy(error = null, isRecovering = false, recoveryMessage = null) }
        loadVideo(_currentVideoId.value)
    }

    private fun autoPlay() {
        viewModelScope.launch {
            delay(200)
            val state = _uiState.value.playerState
            if (state !is PlayerState.Error) {
                engine.play()
                startBackgroundService()
            }
        }
    }

    fun play() {
        engine.play()
        startBackgroundService()
    }

    fun pause() {
        engine.pause()
    }

    fun togglePlayPause() {
        val state = _uiState.value.playerState
        if (state == PlayerState.Playing) engine.pause()
        else {
            engine.play()
            startBackgroundService()
        }
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    fun playNextVideo() {
        val state = _uiState.value
        val queue = state.queue
        val currentIdx = state.queueIndex

        if (state.repeatMode == RepeatMode.ONE) {
            engine.seekTo(0L)
            engine.play()
            return
        }

        if (!settingsManager.autoplay) return

        if (currentIdx in queue.indices && currentIdx < queue.size - 1) {
            val nextIdx = currentIdx + 1
            _uiState.update { it.copy(queueIndex = nextIdx) }
            loadVideo(queue[nextIdx].videoId)
        } else if (state.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            _uiState.update { it.copy(queueIndex = 0) }
            loadVideo(queue[0].videoId)
        } else {
            val related = state.relatedVideos
            val currentId = _currentVideoId.value
            val nextVideo = related.firstOrNull { it.id != currentId }
            if (nextVideo != null) {
                loadVideo(nextVideo.id)
            }
        }
    }

    private fun startBackgroundService() {
        if (!settingsManager.backgroundPlay) return
        val video = _uiState.value.video ?: return
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
            if (PlaybackServiceInstance.connection == null) {
                PlaybackServiceInstance.connection = object : android.content.ServiceConnection {
                    override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                        val svc = (binder as? PlaybackService.LocalBinder)?.getService()
                        PlaybackServiceInstance.service = svc
                        svc?.playVideo(
                            engine,
                            video.title,
                            video.channelName,
                            video.thumbnailUrl
                        )
                    }

                    override fun onServiceDisconnected(name: android.content.ComponentName?) {
                        PlaybackServiceInstance.service = null
                    }
                }
                context.bindService(
                    intent,
                    PlaybackServiceInstance.connection!!,
                    android.content.Context.BIND_AUTO_CREATE
                )
            } else {
                PlaybackServiceInstance.service?.playVideo(
                    engine,
                    video.title,
                    video.channelName,
                    video.thumbnailUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background service: ${e.message}")
        }
    }

    fun enterPipMode() {
        _uiState.update { it.copy(isPipMode = true) }
        startBackgroundService()
    }

    fun exitPipMode() {
        _uiState.update { it.copy(isPipMode = false) }
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean) {
        if (isInPip) {
            enterPipMode()
        } else {
            exitPipMode()
        }
    }

    fun toggleMiniPlayer() {
        if (!settingsManager.miniPlayer) return
        _uiState.update { it.copy(isMiniPlayer = !it.isMiniPlayer) }
    }

    fun enterMiniPlayer() {
        if (!settingsManager.miniPlayer) return
        val state = _uiState.value
        if (state.video != null && state.playerState == PlayerState.Playing || state.playerState == PlayerState.Paused) {
            miniPlayerManager.activate(
                video = state.video!!,
                currentPosition = state.position,
                currentDuration = state.duration,
                currentBufferedPercent = state.bufferedPercent,
                isPlaying = state.playerState == PlayerState.Playing,
                playerEngine = engine
            )
        }
        _uiState.update { it.copy(isMiniPlayer = true) }
    }

    fun exitMiniPlayer() {
        _uiState.update { it.copy(isMiniPlayer = false) }
    }

    fun toggleEqualizer() {
        val enabled = !_uiState.value.equalizerEnabled
        _uiState.update { it.copy(equalizerEnabled = enabled) }
        settingsManager.equalizerEnabled = enabled
        engine.config.equalizerEnabled = enabled
        val eqManager = engine.getEqualizerManager()
        if (enabled && engine.audioSessionId.value != 0) {
            eqManager.initialize(engine.audioSessionId.value)
            eqManager.setEnabled(true)
        } else {
            eqManager.setEnabled(false)
        }
    }

    private fun savePlaybackPosition() {
        val videoId = _currentVideoId.value
        val pos = _uiState.value.position
        if (videoId.isNotBlank() && pos > 0 && settingsManager.rememberPlayback) {
            positionSaveJob?.cancel()
            positionSaveJob = viewModelScope.launch {
                videoDao.updateLastPosition(videoId, pos)
            }
        }
    }

    fun toggleSubscription() {
        val channel = uiState.value.video ?: return
        viewModelScope.launch {
            if (uiState.value.isSubscribed) {
                unsubscribeUseCase(channel.channelId)
            } else {
                subscribeUseCase(channel.channelId)
            }
            _uiState.update { it.copy(isSubscribed = !it.isSubscribed) }
        }
    }

    fun saveToWatchLater() {
        val video = uiState.value.video ?: return
        viewModelScope.launch {
            videoDao.insertWatchLater(
                WatchLaterEntity(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl
                )
            )
            _uiState.update { it.copy(savedToWatchLater = true) }
        }
    }

    fun setQuality(format: VideoFormat) {
        _uiState.update {
            it.copy(
                selectedFormat = format,
                qualityLabel = if (format.height != null) "${format.height}p" else "Audio"
            )
        }

        val videoId = _currentVideoId.value
        viewModelScope.launch {
            getVideoFormatsUseCase(videoId).fold(
                onSuccess = { freshFormats ->
                    _uiState.update { it.copy(formats = freshFormats) }
                    val freshTarget = freshFormats.firstOrNull { it.itag == format.itag }
                    val freshAudio = freshFormats.filter { it.isAdaptive && it.isAudio }.maxByOrNull { it.bitrate }
                    val videoUrl = freshTarget?.url ?: format.url
                    val audioUrl = freshAudio?.url ?: freshFormats.filter { it.isAdaptive && it.isAudio }.maxByOrNull { it.bitrate }?.url
                    doLoadStreams(audioUrl, videoUrl, freshTarget ?: format)
                },
                onFailure = {
                    val bestAudio = _uiState.value.formats.filter { it.isAdaptive && it.isAudio }.maxByOrNull { it.bitrate }
                    doLoadStreams(bestAudio?.url, format.url, format)
                }
            )
        }
    }

    private fun doLoadStreams(audioUrl: String?, videoUrl: String, format: VideoFormat) {
        if (format.isAdaptive && format.isAudio) {
            if (surfaceReady) {
                engine.loadStreams(audioUrl ?: videoUrl, null)
            } else {
                pendingAudioUrl = audioUrl ?: videoUrl
                pendingVideoUrl = null
            }
            _uiState.update { it.copy(isAudioOnly = true) }
        } else {
            if (surfaceReady) {
                engine.loadStreams(audioUrl, videoUrl)
            } else {
                pendingAudioUrl = audioUrl
                pendingVideoUrl = videoUrl
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
                val action = sponsorBlockManager.checkSegments(_currentVideoId.value, state, pos)
                if (action is com.streamvault.player.sponsorblock.SponsorBlockAction.Skip) {
                    val endMs = (action.segment.segment[1] * 1000).toLong()
                    engine.seekTo(endMs)
                }
                delay(500)
            }
        }
    }

    fun startDownload() {
        val video = _uiState.value.video ?: return
        val formats = _uiState.value.formats
        if (formats.isEmpty()) return

        val bestVideo = formats.filter { it.isAdaptive && it.isVideo }
            .maxByOrNull { it.height ?: 0 }
        val bestAudio = formats.filter { it.isAdaptive && it.isAudio }
            .maxByOrNull { it.bitrate }

        if (bestVideo != null && bestAudio != null) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0) }
            downloadManager.startDownload(video, bestAudio.url, bestVideo.url)
            observeDownloadProgress(video.id)
        } else if (bestVideo != null) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0) }
            downloadManager.startDownload(video, "", bestVideo.url)
            observeDownloadProgress(video.id)
        }
    }

    private fun observeDownloadProgress(videoId: String) {
        viewModelScope.launch {
            downloadManager.getDownload(videoId).collect { entity ->
                if (entity == null) {
                    _uiState.update { it.copy(isDownloading = false, isDownloaded = false, downloadProgress = 0) }
                    return@collect
                }
                val isCompleted = entity.downloadStatus == com.streamvault.app.data.local.DownloadStatus.COMPLETED.name
                val isDownloading = entity.downloadStatus == com.streamvault.app.data.local.DownloadStatus.DOWNLOADING.name ||
                    entity.downloadStatus == com.streamvault.app.data.local.DownloadStatus.PENDING.name
                val isPaused = entity.downloadStatus == com.streamvault.app.data.local.DownloadStatus.PAUSED.name
                val isFailed = entity.downloadStatus == com.streamvault.app.data.local.DownloadStatus.FAILED.name

                _uiState.update {
                    it.copy(
                        isDownloaded = isCompleted,
                        isDownloading = isDownloading,
                        isPaused = isPaused,
                        downloadProgress = entity.progress
                    )
                }

                if (isCompleted || isFailed) {
                    return@collect
                }
            }
        }
    }

    fun pauseDownload() {
        viewModelScope.launch {
            downloadManager.pauseDownload(_currentVideoId.value)
            _uiState.update { it.copy(isDownloading = false, isPaused = true) }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            downloadManager.cancelDownload(_currentVideoId.value)
            _uiState.update { it.copy(isDownloading = false, isPaused = false, downloadProgress = 0) }
        }
    }

    fun deleteDownload() {
        viewModelScope.launch {
            downloadManager.deleteDownload(_currentVideoId.value)
            _uiState.update { it.copy(isDownloaded = false, isDownloading = false, downloadProgress = 0) }
        }
    }

    fun checkDownloadStatus() {
        viewModelScope.launch {
            val isDownloaded = downloadManager.isDownloaded(_currentVideoId.value)
            _uiState.update { it.copy(isDownloaded = isDownloaded) }
            if (isDownloaded) {
                observeDownloadProgress(_currentVideoId.value)
            }
        }
    }

    fun playOffline() {
        viewModelScope.launch {
            val filePath = downloadManager.getLocalFilePath(_currentVideoId.value) ?: return@launch
            _uiState.update { it.copy(isPlayingOffline = true, isLoading = true) }

            try {
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    _uiState.update { it.copy(error = "Downloaded file not found", isLoading = false, isPlayingOffline = false) }
                    return@launch
                }

                if (surfaceReady) {
                    engine.loadStreams(null, null, file.absolutePath)
                    autoPlay()
                } else {
                    pendingProgressiveUrl = file.absolutePath
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "playOffline: failed", e)
                _uiState.update { it.copy(error = "Failed to play offline: ${e.message}", isLoading = false, isPlayingOffline = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        } catch (_: Exception) {}
        if (miniPlayerManager.state.value.isActive) {
            // Mini player is active, don't release engine
            return
        }
        savePlaybackPosition()
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.stopService(intent)
        } catch (_: Exception) {}
        engine.release()
    }

}

object PlaybackServiceInstance {
    var service: PlaybackService? = null
    var connection: android.content.ServiceConnection? = null
}
