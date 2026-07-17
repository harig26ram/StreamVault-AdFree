package com.streamvault.player.core

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified playback engine backed by AndroidX Media3 ExoPlayer.
 *
 * The public surface (state/position/duration/bufferedPercent/audioSessionId flows,
 * setSurface/setPlaybackSpeed/loadStreams/play/pause/seekTo/stop/release, config,
 * getEqualizerManager) is identical to the previous custom-MediaCodec engine so that
 * PlayerViewModel, PlaybackService and the unit tests keep working unchanged.
 *
 * ExoPlayer natively handles YouTube's H.264/H.265 (Annex-B conversion is done
 * internally), VP9/WebM and Opus, and fragmented/single MP4 containers — which the
 * previous hand-rolled decoder pipeline could not decode (black surface / "no streams").
 */
class PlayerEngine(val config: PlayerConfig = PlayerConfig(), context: Context? = null) {

    companion object {
        private const val TAG = "PlayerEngine"
    }

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPercent = MutableStateFlow(0)
    val bufferedPercent: StateFlow<Int> = _bufferedPercent.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isPlaying = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var surface: Surface? = null
    private var positionUpdateJob: Job? = null
    private var silenceDetectionJob: Job? = null

    private val equalizerManager = EqualizerManager()

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        )
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com"
            )
        )

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> { /* no-op */ }
                Player.STATE_BUFFERING -> _state.value = PlayerState.Buffering
                Player.STATE_READY ->
                    _state.value =
                        if (exoPlayer?.playWhenReady == true) PlayerState.Playing else PlayerState.Paused
                Player.STATE_ENDED -> _state.value = PlayerState.Ended
            }
            syncProgress()
        }

        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            if (isPlayingNow) {
                _state.value = PlayerState.Playing
            } else if (_state.value !is PlayerState.Ended && _state.value !is PlayerState.Error) {
                _state.value = PlayerState.Paused
            }
            syncProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.message}", error)
            val is403 = error.message?.contains("403") == true ||
                error.message?.contains("Forbidden") == true ||
                error.cause?.message?.contains("403") == true
            val errorMsg = if (is403) {
                "Playback error (403 Forbidden)"
            } else {
                "Playback error: ${error.message}"
            }
            _state.value = PlayerState.Error(errorMsg, error)
        }
    }

    private var exoPlayer: ExoPlayer? = null

    init {
        exoPlayer = context?.let { ctx ->
            try {
                ExoPlayer.Builder(ctx).build().apply {
                    addListener(playerListener)
                    playWhenReady = false
                    // Capture and publish the real audio session ID for equalizer
                    _audioSessionId.value = audioSessionId
                    EqualizerManagerHolder.register(equalizerManager, audioSessionId)
                    equalizerManager.initialize(audioSessionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build ExoPlayer", e)
                null
            }
        }
        if (context != null && exoPlayer == null) {
            Log.e(TAG, "ExoPlayer could not be initialized")
        }
    }

    private fun syncProgress() {
        val p = exoPlayer ?: return
        _position.value = p.currentPosition.coerceAtLeast(0L)
        val d = p.duration
        _duration.value = if (d == C.TIME_UNSET) 0L else d.coerceAtLeast(0L)
        _bufferedPercent.value = p.bufferedPercentage
    }

    fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface: surface=${surface != null}")
        this.surface = surface
        try {
            exoPlayer?.setVideoSurface(surface)
        } catch (e: Exception) {
            Log.e(TAG, "setVideoSurface failed", e)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        config.playbackSpeed = speed.coerceIn(0.25f, 4.0f)
        try {
            exoPlayer?.setPlaybackSpeed(config.playbackSpeed)
        } catch (_: Exception) {
        }
    }

    fun loadStreams(
        audioUrl: String?,
        videoUrl: String?,
        progressiveUrl: String? = null
    ) {
        val player = exoPlayer ?: run {
            _state.value = PlayerState.Error("Player not initialized (missing context)")
            return
        }
        if (_state.value !is PlayerState.Idle && _state.value !is PlayerState.Error) {
            stop()
        }
        _state.value = PlayerState.Buffering
        isPlaying.set(false)
        isPaused.set(false)

        try {
            val factory = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            val source: MediaSource = when {
                progressiveUrl != null ->
                    factory.createMediaSource(MediaItem.fromUri(progressiveUrl))
                audioUrl != null && videoUrl != null ->
                    MergingMediaSource(
                        factory.createMediaSource(MediaItem.fromUri(audioUrl)),
                        factory.createMediaSource(MediaItem.fromUri(videoUrl))
                    )
                videoUrl != null ->
                    factory.createMediaSource(MediaItem.fromUri(videoUrl))
                audioUrl != null ->
                    factory.createMediaSource(MediaItem.fromUri(audioUrl))
                else -> {
                    _state.value = PlayerState.Error("No stream URLs provided")
                    return
                }
            }
            player.setMediaSource(source, true)
            player.prepare()
            syncProgress()
        } catch (e: Exception) {
            Log.e(TAG, "loadStreams failed", e)
            _state.value = PlayerState.Error("Load failed: ${e.message}", e)
        }
    }

    fun play() {
        if (_state.value is PlayerState.Idle) return
        if (_state.value is PlayerState.Ended) {
            seekTo(0L)
            return
        }
        isPaused.set(false)
        isPlaying.set(true)
        try {
            exoPlayer?.play()
        } catch (e: Exception) {
            Log.e(TAG, "play failed", e)
        }
        _state.value = PlayerState.Playing
        startPositionUpdates()
        startSilenceDetection()
    }

    fun pause() {
        if (_state.value !is PlayerState.Playing && _state.value !is PlayerState.Buffering) return
        isPaused.set(true)
        isPlaying.set(false)
        try {
            exoPlayer?.pause()
        } catch (_: Exception) {
        }
        _state.value = PlayerState.Paused
        silenceDetectionJob?.cancel()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        try {
            exoPlayer?.seekTo(target)
        } catch (e: Exception) {
            _state.value = PlayerState.Error("Seek failed: ${e.message}", e)
            return
        }
        _state.value = PlayerState.Buffering
        scope.launch(Dispatchers.Main) {
            delay(150)
            if (isPlaying.get()) _state.value = PlayerState.Playing else _state.value = PlayerState.Paused
            syncProgress()
        }
    }

    fun stop() {
        isPlaying.set(false)
        isPaused.set(false)
        positionUpdateJob?.cancel()
        silenceDetectionJob?.cancel()
        try {
            exoPlayer?.stop()
        } catch (_: Exception) {
        }
        _position.value = 0L
        _bufferedPercent.value = 0
        if (_state.value !is PlayerState.Error) {
            _state.value = PlayerState.Idle
        }
    }

    fun release() {
        stop()
        try {
            equalizerManager.release()
        } catch (_: Exception) {
        }
        try {
            exoPlayer?.release()
        } catch (_: Exception) {
        }
        scope.cancel()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(250)
                val p = exoPlayer ?: break
                _position.value = p.currentPosition.coerceAtLeast(0L)
                val d = p.duration
                if (d != C.TIME_UNSET) _duration.value = d.coerceAtLeast(0L)
                _bufferedPercent.value = p.bufferedPercentage
            }
        }
    }

    private fun startSilenceDetection() {
        silenceDetectionJob?.cancel()
        if (!config.skipSilenceEnabled) return
        silenceDetectionJob = scope.launch(Dispatchers.Main) {
            var lastPosition = _position.value
            var lastChangeTime = System.currentTimeMillis()
            while (isActive) {
                delay(100)
                val currentPos = _position.value
                val now = System.currentTimeMillis()
                if (currentPos != lastPosition) {
                    lastPosition = currentPos
                    lastChangeTime = now
                } else if (_state.value is PlayerState.Playing) {
                    val silenceDuration = now - lastChangeTime
                    if (silenceDuration > config.silenceSkipDurationMs) {
                        val skipTo = currentPos + 1000L
                        Log.d(TAG, "Silence detected for ${silenceDuration}ms, skipping to $skipTo")
                        seekTo(skipTo)
                        lastPosition = skipTo
                        lastChangeTime = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun getEqualizerManager(): EqualizerManager = equalizerManager
}
