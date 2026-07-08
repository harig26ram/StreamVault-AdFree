package com.streamvault.app.presentation.ui.components

import com.streamvault.app.domain.model.Video
import com.streamvault.player.core.PlayerEngine
import com.streamvault.player.core.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class MiniPlayerState(
    val isActive: Boolean = false,
    val video: Video? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercent: Int = 0,
    val thumbnailUrl: String? = null,
    val isDownloaded: Boolean = false,
    val downloadProgress: Int = 0
)

@Singleton
class MiniPlayerManager @Inject constructor() {
    private val _state = MutableStateFlow(MiniPlayerState())
    val state: StateFlow<MiniPlayerState> = _state.asStateFlow()

    private var engine: PlayerEngine? = null

    fun activate(
        video: Video,
        currentPosition: Long,
        currentDuration: Long,
        currentBufferedPercent: Int,
        isPlaying: Boolean,
        playerEngine: PlayerEngine
    ) {
        engine = playerEngine
        _state.update {
            it.copy(
                isActive = true,
                video = video,
                position = currentPosition,
                duration = currentDuration,
                bufferedPercent = currentBufferedPercent,
                isPlaying = isPlaying,
                thumbnailUrl = video.thumbnailUrl,
                isDownloaded = false,
                downloadProgress = 0
            )
        }
    }

    fun deactivate() {
        _state.update { it.copy(isActive = false) }
        engine = null
    }

    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long, bufferedPercent: Int) {
        _state.update {
            it.copy(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                bufferedPercent = bufferedPercent
            )
        }
    }

    fun updateDownloadState(isDownloaded: Boolean, progress: Int) {
        _state.update {
            it.copy(isDownloaded = isDownloaded, downloadProgress = progress)
        }
    }

    fun getEngine(): PlayerEngine? = engine
}
