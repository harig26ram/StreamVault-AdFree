package com.streamvault.player.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import java.nio.ByteBuffer

class AudioTrackBufferProvider {
    private var audioTrack: AudioTrack? = null
    private var configuredSampleRate = 0
    private var configuredChannelConfig = 0
    private var configuredAudioFormat = 0

    val isInitialized: Boolean get() = audioTrack?.state == AudioTrack.STATE_INITIALIZED

    val audioSessionId: Int get() = audioTrack?.audioSessionId ?: 0

    fun setup(
        sampleRate: Int,
        channelConfig: Int = AudioFormat.CHANNEL_OUT_STEREO,
        audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
    ): Boolean {
        release()
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) return false
        val bufferSize = minBufferSize.coerceAtLeast(16384) * 8
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(audioFormat)
            .build()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack = null
            return false
        }
        configuredSampleRate = sampleRate
        configuredChannelConfig = channelConfig
        configuredAudioFormat = audioFormat
        return true
    }

    fun play() {
        audioTrack?.play()
    }

    fun pause() {
        audioTrack?.pause()
    }

    fun stop() {
        audioTrack?.stop()
    }

    fun flush() {
        audioTrack?.flush()
    }

    fun write(data: ByteBuffer, offset: Int, size: Int): Int {
        val track = audioTrack ?: return AudioTrack.ERROR_INVALID_OPERATION
        val available = data.capacity() - offset
        val safeSize = minOf(size, available.coerceAtLeast(0))
        if (safeSize <= 0) return 0
        val bytes = ByteArray(safeSize)
        val pos = data.position()
        data.position(offset)
        data.get(bytes, 0, safeSize)
        data.position(pos)
        return track.write(bytes, 0, safeSize)
    }

    fun write(bytes: ByteArray, offset: Int, size: Int): Int {
        return audioTrack?.write(bytes, offset, size) ?: AudioTrack.ERROR_INVALID_OPERATION
    }

    val playbackPositionUs: Long
        get() {
            val track = audioTrack ?: return 0L
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                val frames = track.playbackHeadPosition
                return if (configuredSampleRate > 0) {
                    (frames.toLong() * 1_000_000L) / configuredSampleRate
                } else 0L
            }
            val ts = AudioTimestamp()
            return if (track.getTimestamp(ts)) {
                (ts.framePosition.toLong() * 1_000_000L) / configuredSampleRate
            } else {
                val frames = track.playbackHeadPosition
                (frames.toLong() * 1_000_000L) / configuredSampleRate
            }
        }

    fun setVolume(volume: Float) {
        audioTrack?.setVolume(volume)
    }

    fun setPlaybackSpeed(speed: Float) {
        val rate = (configuredSampleRate * speed).toInt().coerceIn(4000, 192000)
        audioTrack?.setPlaybackRate(rate)
    }

    val minBufferSize: Int
        get() = if (configuredSampleRate > 0) {
            AudioTrack.getMinBufferSize(
                configuredSampleRate,
                configuredChannelConfig,
                configuredAudioFormat
            ).coerceAtLeast(4096)
        } else 4096

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        configuredSampleRate = 0
    }
}
