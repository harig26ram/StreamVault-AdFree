package com.streamvault.player.core

import android.util.Log
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
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

class PlayerEngine(val config: PlayerConfig = PlayerConfig()) {
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

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackClock = PlaybackClock()
    private var audioTrackProvider = AudioTrackBufferProvider()
    private var surface: Surface? = null

    private var videoFetcher: DataSource? = null
    private var audioFetcher: DataSource? = null

    private var videoDecoder: DecoderThread? = null
    private var audioDecoder: DecoderThread? = null

    private var videoExtractor: MediaExtractor? = null
    private var audioExtractor: MediaExtractor? = null

    private val isPlaying = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var isProgressive = false

    private var positionUpdateJob: Job? = null
    private var silenceDetectionJob: Job? = null
    private val equalizerManager = EqualizerManager()

    fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface: surface=${surface != null}")
        this.surface = surface
    }

    fun setPlaybackSpeed(speed: Float) {
        config.playbackSpeed = speed.coerceIn(0.25f, 4.0f)
        playbackClock.setSpeed(config.playbackSpeed)
        audioTrackProvider.setPlaybackSpeed(config.playbackSpeed)
    }

    fun loadStreams(
        audioUrl: String?,
        videoUrl: String?,
        progressiveUrl: String? = null
    ) {
        Log.d(TAG, "loadStreams: audio=${audioUrl?.take(80)}, video=${videoUrl?.take(80)}, progressive=${progressiveUrl?.take(80)}, surface=${surface != null}")
        if (_state.value !is PlayerState.Idle && _state.value !is PlayerState.Error) {
            stop()
        }
        _state.value = PlayerState.Buffering
        isPlaying.set(false)
        isPaused.set(false)
        isProgressive = progressiveUrl != null

        scope.launch(Dispatchers.IO) {
            try {
                if (isProgressive) {
                    if (progressiveUrl == null) {
                        Log.e(TAG, "loadStreams: progressiveUrl is null but isProgressive=true")
                        _state.value = PlayerState.Error("Progressive URL is null")
                        return@launch
                    }
                    loadProgressive(progressiveUrl)
                } else {
                    loadAdaptive(audioUrl ?: "", videoUrl ?: "")
                }
                Log.d(TAG, "loadStreams: load completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "loadStreams: FAILED", e)
                _state.value = PlayerState.Error("Load failed: ${e.message}", e)
            }
        }
    }

    private suspend fun loadProgressive(url: String) {
        Log.d(TAG, "loadProgressive: starting with url=${url.take(80)}")
        val probeExt = MediaExtractor()
        probeExt.setDataSource(url)

        val videoTrackIdx = findTrackIndex(probeExt, true)
        val audioTrackIdx = findTrackIndex(probeExt, false)
        Log.d(TAG, "loadProgressive: videoTrackIdx=$videoTrackIdx, audioTrackIdx=$audioTrackIdx")

        if (videoTrackIdx < 0 && audioTrackIdx < 0) {
            probeExt.release()
            throw IllegalStateException("No supported tracks in progressive stream")
        }

        var videoDur = 0L
        var audioDur = 0L

        if (videoTrackIdx >= 0) {
            val vExt = MediaExtractor()
            vExt.setDataSource(url)
            vExt.selectTrack(videoTrackIdx)
            videoExtractor = vExt
            val fmt = vExt.getTrackFormat(videoTrackIdx)
            videoDur = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            videoDur /= 1000L
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            val ds = MediaExtractorDataSource(vExt, mime, fmt, true)
            videoFetcher = ds
            val videoFormat = ds.open()
            if (videoFormat == null) {
                Log.e(TAG, "Failed to open video data source")
                throw IllegalStateException("Failed to open video data source")
            }
            videoDecoder = DecoderThread(
                name = "prog-video",
                mimeType = mime,
                formatInfo = videoFormat,
                dataSource = ds,
                isVideo = true,
                surface = surface,
                playbackClock = playbackClock,
                stateSink = { updateState(it) },
                updatePosition = { _position.value = it },
                updateBuffered = { _bufferedPercent.value = it }
            )
        }

        if (audioTrackIdx >= 0) {
            val aExt = MediaExtractor()
            aExt.setDataSource(url)
            aExt.selectTrack(audioTrackIdx)
            audioExtractor = aExt
            val fmt = aExt.getTrackFormat(audioTrackIdx)
            audioDur = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            audioDur /= 1000L
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val ds = MediaExtractorDataSource(aExt, mime, fmt, false)
            audioFetcher = ds

            if (!audioTrackProvider.isInitialized) {
                val sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
                val chCount = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                val cc = if (chCount == 1) {
                    android.media.AudioFormat.CHANNEL_OUT_MONO
                } else {
                    android.media.AudioFormat.CHANNEL_OUT_STEREO
                }
                audioTrackProvider.setup(sr, cc)
            }

            val audioFormat = ds.open()
            if (audioFormat == null) {
                Log.e(TAG, "Failed to open audio data source")
                throw IllegalStateException("Failed to open audio data source")
            }
            audioDecoder = DecoderThread(
                name = "prog-audio",
                mimeType = mime,
                formatInfo = audioFormat,
                dataSource = ds,
                isVideo = false,
                audioTrackProvider = audioTrackProvider,
                playbackClock = playbackClock,
                stateSink = { updateState(it) },
                updatePosition = {},
                updateBuffered = { _bufferedPercent.value = it }
            )
        }

        probeExt.release()
        _duration.value = maxOf(videoDur, audioDur) / 1000L
        initEqualizerIfNeeded()
        videoDecoder?.start()
        audioDecoder?.start()
        playbackClock.start()
    }

    private suspend fun loadAdaptive(audioUrl: String, videoUrl: String) {
        Log.d(TAG, "loadAdaptive: audio=${audioUrl.take(80)}, video=${videoUrl.take(80)}")
        Log.d(TAG, "loadAdaptive: surface=${surface != null}")

        val audioFetch = HttpStreamFetcher(audioUrl)
        audioFetcher = audioFetch
        val audioFormat = audioFetch.open()
            ?: throw IllegalStateException("Could not parse audio stream")
        Log.d(TAG, "loadAdaptive: audio parsed, mimeType=${audioFormat.mimeType}, sampleRate=${audioFormat.sampleRate}, channels=${audioFormat.channelCount}")

        val videoFetch = HttpStreamFetcher(videoUrl)
        videoFetcher = videoFetch
        val videoFormat = videoFetch.open()
            ?: throw IllegalStateException("Could not parse video stream")
        Log.d(TAG, "loadAdaptive: video parsed, mimeType=${videoFormat.mimeType}, width=${videoFormat.width}, height=${videoFormat.height}")

        _duration.value = maxOf(videoFormat.durationUs, audioFormat.durationUs) / 1000L

        if (!audioTrackProvider.isInitialized) {
            val sr = if (audioFormat.sampleRate > 0) audioFormat.sampleRate else 44100
            val cc = if (audioFormat.channelCount == 1) {
                android.media.AudioFormat.CHANNEL_OUT_MONO
            } else {
                android.media.AudioFormat.CHANNEL_OUT_STEREO
            }
            audioTrackProvider.setup(sr, cc)
        }

        audioDecoder = DecoderThread(
            name = "adp-audio",
            mimeType = audioFormat.mimeType,
            formatInfo = audioFormat,
            dataSource = audioFetch,
            isVideo = false,
            audioTrackProvider = audioTrackProvider,
            playbackClock = playbackClock,
            stateSink = { updateState(it) },
            updatePosition = {},
            updateBuffered = { _bufferedPercent.value = it }
        )

        videoDecoder = DecoderThread(
            name = "adp-video",
            mimeType = videoFormat.mimeType,
            formatInfo = videoFormat,
            dataSource = videoFetch,
            isVideo = true,
            surface = surface,
            playbackClock = playbackClock,
            stateSink = { updateState(it) },
            updatePosition = { _position.value = it },
            updateBuffered = { _bufferedPercent.value = it }
        )

        audioDecoder?.start()
        videoDecoder?.start()
        playbackClock.start()
        initEqualizerIfNeeded()
        Log.d(TAG, "loadAdaptive: decoders started, clock started, duration=${_duration.value}ms")
    }

    fun play() {
        Log.d(TAG, "play: state=${_state.value}, isPaused=${isPaused.get()}, isPlaying=${isPlaying.get()}")
        if (_state.value is PlayerState.Playing) return
        if (_state.value is PlayerState.Ended) {
            seekTo(0L)
            return
        }
        if (_state.value is PlayerState.Idle) return
        isPaused.set(false)
        isPlaying.set(true)
        playbackClock.resume()
        videoDecoder?.resume()
        audioDecoder?.resume()
        audioTrackProvider.play()
        _state.value = PlayerState.Playing
        startPositionUpdates()
        startSilenceDetection()
    }

    fun pause() {
        if (_state.value !is PlayerState.Playing && _state.value !is PlayerState.Buffering) return
        isPaused.set(true)
        isPlaying.set(false)
        playbackClock.pause()
        audioTrackProvider.pause()
        videoDecoder?.pause()
        audioDecoder?.pause()
        _state.value = PlayerState.Paused
        silenceDetectionJob?.cancel()
    }

    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        _state.value = PlayerState.Buffering
        scope.launch(Dispatchers.IO) {
            try {
                val targetUs = targetMs * 1000L
                playbackClock.startAt(targetUs, config.playbackSpeed)

                if (isProgressive) {
                    videoExtractor?.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    audioExtractor?.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    videoDecoder?.requestSeek()
                    audioDecoder?.requestSeek()
                } else {
                    videoFetcher?.seekTo(targetUs, -1L)
                    audioFetcher?.seekTo(targetUs, -1L)
                    videoDecoder?.requestSeek()
                    audioDecoder?.requestSeek()
                }

                audioTrackProvider.flush()

                if (isPlaying.get()) {
                    playbackClock.resume()
                    audioTrackProvider.play()
                    videoDecoder?.resume()
                    audioDecoder?.resume()
                    _state.value = PlayerState.Playing
                } else {
                    _state.value = PlayerState.Paused
                }
            } catch (e: Exception) {
                _state.value = PlayerState.Error("Seek failed: ${e.message}", e)
            }
        }
    }

    fun stop() {
        isPlaying.set(false)
        isPaused.set(false)
        positionUpdateJob?.cancel()
        silenceDetectionJob?.cancel()
        playbackClock.reset()
        videoDecoder?.stop()
        audioDecoder?.stop()
        audioTrackProvider.stop()
        videoDecoder?.release()
        audioDecoder?.release()
        videoFetcher?.close()
        audioFetcher?.close()
        videoExtractor?.release()
        audioExtractor?.release()
        videoExtractor = null
        audioExtractor = null
        videoDecoder = null
        audioDecoder = null
        videoFetcher = null
        audioFetcher = null
        _position.value = 0L
        _bufferedPercent.value = 0
        if (_state.value !is PlayerState.Error) {
            _state.value = PlayerState.Idle
        }
    }

    fun release() {
        stop()
        EqualizerManagerHolder.unregister()
        equalizerManager.release()
        audioTrackProvider.release()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    private fun findTrackIndex(extractor: MediaExtractor, wantVideo: Boolean): Int {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (wantVideo == mime.startsWith("video/")) return i
        }
        return -1
    }

    private fun updateState(newState: PlayerState) {
        if (newState is PlayerState.Ended && (isPlaying.get() || isPaused.get())) {
            _state.value = newState
        } else if (newState is PlayerState.Error) {
            _state.value = newState
        } else if (newState is PlayerState.Playing && _state.value !is PlayerState.Error) {
            _state.value = newState
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(250)
                val pos = playbackClock.mediaTimeMs
                if (pos > _position.value) {
                    _position.value = pos
                }
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

    private fun initEqualizerIfNeeded() {
        try {
            val sid = audioTrackProvider.audioSessionId
            _audioSessionId.value = sid
            if (sid != 0 && config.equalizerEnabled) {
                equalizerManager.initialize(sid)
                equalizerManager.setEnabled(true)
                EqualizerManagerHolder.register(equalizerManager, sid)
                Log.d(TAG, "Equalizer initialized with sessionId=$sid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init equalizer: ${e.message}")
        }
    }

    fun getEqualizerManager(): EqualizerManager = equalizerManager
}
