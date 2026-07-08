package com.streamvault.app.cast

import android.util.Log
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.model.VideoFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastPlayer @Inject constructor(
    private val castSessionManager: CastSessionManager
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castState = MutableStateFlow<CastPlaybackState>(CastPlaybackState.Idle)
    val castState: StateFlow<CastPlaybackState> = _castState.asStateFlow()

    fun castVideo(video: Video, format: VideoFormat?) {
        Log.d(TAG, "Cast not available: ${video.title}")
    }

    fun sendUrlToCast(videoId: String, videoUrl: String, title: String, thumbnailUrl: String) {
        Log.d(TAG, "Cast not available: $title")
    }

    fun play() {}
    fun pause() {}
    fun togglePlayPause() {}
    fun seekTo(positionMs: Long) {}
    fun stop() {}
    fun handleDisconnect() {
        _isCasting.value = false
        _castState.value = CastPlaybackState.Idle
    }

    sealed class CastPlaybackState {
        object Idle : CastPlaybackState()
        object Loading : CastPlaybackState()
        object Playing : CastPlaybackState()
        object Paused : CastPlaybackState()
        object Buffering : CastPlaybackState()
        object Finished : CastPlaybackState()
        data class Error(val message: String) : CastPlaybackState()
    }

    companion object {
        private const val TAG = "CastPlayer"
    }
}
