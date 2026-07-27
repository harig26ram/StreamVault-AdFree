package com.freedomplay.app.presentation.ui.screens.player

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.freedomplay.app.domain.model.Stream

class HdExoPlayerState {
    var exoPlayer: ExoPlayer? = null
        internal set

    var currentPosition: Float by mutableStateOf(0f)
        private set

    var duration: Float by mutableStateOf(0f)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlay() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionSeconds: Float) {
        exoPlayer?.seekTo((positionSeconds * 1000f).toLong())
    }

    fun skipForward(seconds: Long = 10L) {
        val player = exoPlayer ?: return
        val newPos = player.currentPosition + (seconds * 1000L)
        player.seekTo(minOf(newPos, player.duration.coerceAtLeast(0L)))
    }

    fun skipBackward(seconds: Long = 10L) {
        val player = exoPlayer ?: return
        val newPos = player.currentPosition - (seconds * 1000L)
        player.seekTo(maxOf(newPos, 0L))
    }

    fun poll() {
        val player = exoPlayer ?: return
        currentPosition = player.currentPosition / 1000f
        duration = player.duration / 1000f
        isPlaying = player.isPlaying
    }
}

@Composable
fun rememberHdExoPlayerState(): HdExoPlayerState {
    return remember { HdExoPlayerState() }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun HdExoPlayerView(
    videoId: String,
    stream: Stream,
    hdPlayerState: HdExoPlayerState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoId) {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setForceHighestSupportedBitrate(true))
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply { playWhenReady = true }
    }

    LaunchedEffect(videoId, stream) {
        val source = PlaybackSourceFactory.build(context, stream, "Auto", false)
        if (source != null) {
            exoPlayer.stop()
            exoPlayer.setMediaSource(source)
            exoPlayer.prepare()
        }
    }

    DisposableEffect(videoId) {
        hdPlayerState.exoPlayer = exoPlayer
        onDispose {
            hdPlayerState.exoPlayer = null
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        }
    )
}
