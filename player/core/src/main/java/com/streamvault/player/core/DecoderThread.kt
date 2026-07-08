package com.streamvault.player.core

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class DecoderThread(
    private val name: String,
    private val mimeType: String,
    private val formatInfo: FormatInfo,
    private val dataSource: DataSource,
    private val isVideo: Boolean,
    private val surface: Surface? = null,
    private val audioTrackProvider: AudioTrackBufferProvider? = null,
    private val playbackClock: PlaybackClock? = null,
    private val stateSink: (PlayerState) -> Unit = {},
    private val updatePosition: (Long) -> Unit = {},
    private val updateBuffered: (Int) -> Unit = {}
) {
    private var mediaCodec: MediaCodec? = null
    private var decodeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile private var paused = false
    @Volatile private var ended = false
    @Volatile private var isDecoding = false
    private val seekPending = AtomicBoolean(false)

    private var inputChunkSize = 256 * 1024
    private var pendingEos = false
    private var eosReceived = false
    private var eosOutput = false
    private var outputFormat: MediaFormat? = null

    val hasEnded: Boolean get() = eosOutput

    fun start() {
        if (isDecoding) return
        ended = false
        eosReceived = false
        eosOutput = false
        pendingEos = false
        isDecoding = true
        decodeJob = scope.launch {
            try {
                initCodec()
                decodeLoop()
            } catch (e: Exception) {
                if (isActive) {
                    stateSink(PlayerState.Error("$name failed: ${e.message}", e))
                }
            } finally {
                releaseCodec()
                isDecoding = false
            }
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun requestSeek() {
        seekPending.set(true)
    }

    fun stop() {
        isDecoding = false
        decodeJob?.cancel()
        decodeJob = null
    }

    fun release() {
        stop()
        scope.cancel()
        releaseCodec()
    }

    private fun initCodec() {
        val codecName = findCodec()
        val codec = MediaCodec.createByCodecName(codecName)
        val format = createMediaFormat()
        if (isVideo && surface != null) {
            codec.configure(format, surface, null, 0)
        } else {
            codec.configure(format, null, null, 0)
        }
        codec.start()
        mediaCodec = codec
    }

    private fun findCodec(): String {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) {
                for (mime in info.supportedTypes) {
                    if (mime.equals(mimeType, ignoreCase = true)) {
                        return info.name
                    }
                }
            }
        }
        throw IllegalStateException("No decoder found for $mimeType")
    }

    private fun createMediaFormat(): MediaFormat {
        return if (isVideo) {
            val width = if (formatInfo.width > 0) formatInfo.width else 640
            val height = if (formatInfo.height > 0) formatInfo.height else 360
            val format = MediaFormat.createVideoFormat(mimeType, width, height)
            format.setInteger(MediaFormat.KEY_MAX_WIDTH, 1920)
            format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 1080)
            for ((index, csd) in formatInfo.csd.withIndex()) {
                val key = when (index) {
                    0 -> "csd-0"
                    1 -> "csd-1"
                    2 -> "csd-2"
                    else -> "csd-$index"
                }
                format.setByteBuffer(key, ByteBuffer.wrap(csd))
            }
            format
        } else {
            val sampleRate = if (formatInfo.sampleRate > 0) formatInfo.sampleRate else 44100
            val channels = if (formatInfo.channelCount > 0) formatInfo.channelCount else 2
            val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channels)
            for ((index, csd) in formatInfo.csd.withIndex()) {
                val key = when (index) {
                    0 -> "csd-0"
                    else -> "csd-$index"
                }
                format.setByteBuffer(key, ByteBuffer.wrap(csd))
            }
            format
        }
    }

    private suspend fun decodeLoop() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val tempBuffer = ByteBuffer.allocate(inputChunkSize)

        while (isDecoding && !eosOutput) {
            if (seekPending.getAndSet(false)) {
                codec.flush()
                pendingEos = false
                eosReceived = false
                eosOutput = false
                continue
            }

            while (paused && isDecoding) {
                kotlinx.coroutines.delay(50)
            }

            if (!isDecoding) break

            if (!pendingEos && !eosReceived) {
                val inputIndex = codec.dequeueInputBuffer(5000L)
                if (inputIndex >= 0) {
                    val buf = codec.getInputBuffer(inputIndex) ?: continue
                    buf.clear()
                    tempBuffer.clear()
                    val maxRead = minOf(inputChunkSize, buf.capacity())
                    val bytesRead = dataSource.readRaw(tempBuffer, maxRead)
                    if (bytesRead < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        pendingEos = true
                        eosReceived = true
                    } else {
                        tempBuffer.flip()
                        val safeRead = minOf(bytesRead, buf.capacity())
                        buf.put(tempBuffer.apply { limit(minOf(limit(), safeRead)) })
                        codec.queueInputBuffer(
                            inputIndex, 0, safeRead, 0L, 0
                        )
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    updateBuffered(50)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = codec.outputFormat
                    if (!isVideo && audioTrackProvider != null) {
                        val fmt = codec.outputFormat
                        val sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else 44100
                        val channelCount = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else 2
                        val channelConfig = if (channelCount == 1) {
                            android.media.AudioFormat.CHANNEL_OUT_MONO
                        } else {
                            android.media.AudioFormat.CHANNEL_OUT_STEREO
                        }
                        val pcmEncoding = if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else android.media.AudioFormat.ENCODING_PCM_16BIT
                        if (!audioTrackProvider.isInitialized) {
                            audioTrackProvider.setup(sampleRate, channelConfig, pcmEncoding)
                        }
                        audioTrackProvider.play()
                    }
                }
                outputIndex >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosOutput = true
                    }
                    if (isVideo) {
                        val ptsUs = bufferInfo.presentationTimeUs
                        val targetNs = playbackClock?.targetWakeupTimeNsForPts(ptsUs) ?: 0L
                        if (targetNs > 0L) {
                            val now = System.nanoTime()
                            val waitNs = targetNs - now
                            if (waitNs > 0L) {
                                val waitMs = waitNs / 1_000_000L
                                delay(waitMs.coerceAtMost(10000))
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, true)
                        updatePosition(ptsUs / 1000L)
                    } else {
                        if (audioTrackProvider != null && audioTrackProvider.isInitialized) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                val safeSize = minOf(bufferInfo.size, outputBuffer.capacity() - bufferInfo.offset)
                                if (safeSize > 0) {
                                    audioTrackProvider.write(
                                        outputBuffer,
                                        bufferInfo.offset,
                                        safeSize
                                    )
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    updateBuffered(100)
                    stateSink(PlayerState.Playing)
                }
            }
        }

        ended = true
        if (eosOutput) {
            stateSink(PlayerState.Ended)
        }
    }

    private fun releaseCodec() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null
    }
}
