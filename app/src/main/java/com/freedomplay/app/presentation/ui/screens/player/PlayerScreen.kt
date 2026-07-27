package com.freedomplay.app.presentation.ui.screens.player

import android.app.PictureInPictureParams
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.freedomplay.app.presentation.ui.components.formatViews
import com.freedomplay.app.presentation.viewmodel.PlayerViewModel
import com.freedomplay.app.util.TimeUtils
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowInsetsController
import android.content.pm.ActivityInfo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Player screen. Video always plays in HD via YouTube's own web player, confined to a pinned
 * 16:9 surface at the top (no app buttons overlay it — YouTube's native controls, including
 * the quality gear, are the player UI; quality is forced to max by default). Everything below
 * the player is the app's own native UI: title, actions, description and the ad-filtered
 * "Up Next" list. Audio-only mode uses the native ExoPlayer path for background playback.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    onVideoLoaded: (title: String, thumbnail: String) -> Unit = { _, _ -> },
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val relatedVideos by viewModel.relatedVideos.collectAsStateWithLifecycle()
    val isLoadingRelated by viewModel.isLoadingRelated.collectAsStateWithLifecycle()
    val audioOnly by viewModel.audioOnly.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val rememberPosition by viewModel.rememberPosition.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The video currently playing. Related-video taps swap this in place (player + feed reload)
    // instead of stacking another Player destination on the back stack.
    var currentVideoId by rememberSaveable(videoId) { mutableStateOf(videoId) }

    LaunchedEffect(currentVideoId) {
        viewModel.loadVideo(currentVideoId)
    }

    LaunchedEffect(stream) {
        stream?.let { onVideoLoaded(it.title, it.thumbnailUrl ?: "") }
    }

    var isPiPActive by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isTouchLocked by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    val hdPlayerState = rememberHdExoPlayerState()
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val callback = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isPiPActive = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(callback)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(callback)
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(currentVideoId) {
        while (isActive) {
            hdPlayerState.poll()
            delay(500)
        }
    }

    fun toggleFullscreen() {
        val act = context as? ComponentActivity ?: return
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            act.window.insetsController?.hide(
                AndroidWindowInsets.Type.systemBars()
            )
            act.window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            act.window.insetsController?.show(
                AndroidWindowInsets.Type.systemBars()
            )
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading video...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error ?: "Failed to load",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadVideo(currentVideoId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                stream?.let { currentStream ->
                    if (!audioOnly) {
                        // ---- HD mode: pinned web player, native UI below ----
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFullscreen) Modifier.fillMaxSize()
                                    else Modifier.aspectRatio(16f / 9f)
                                )
                                .background(Color.Black)
                        ) {
                            key(currentVideoId) {
                                HdExoPlayerView(
                                    videoId = currentVideoId,
                                    stream = currentStream,
                                    hdPlayerState = hdPlayerState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Tap to toggle controls
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { controlsVisible = !controlsVisible }
                                        )
                                    }
                            )

                            // Double-tap skip overlay
                            DoubleTapSkipOverlay(
                                onSkipForward = { hdPlayerState.skipForward() },
                                onSkipBackward = { hdPlayerState.skipBackward() }
                            )

                            BottomPlayerControls(
                                duration = hdPlayerState.duration,
                                currentPosition = hdPlayerState.currentPosition,
                                isPlaying = hdPlayerState.isPlaying,
                                onTogglePlay = { hdPlayerState.togglePlay() },
                                onSeek = { pos -> hdPlayerState.seekTo(pos) },
                                onSkipForward = { hdPlayerState.skipForward() },
                                onSkipBackward = { hdPlayerState.skipBackward() },
                                onToggleFullscreen = { toggleFullscreen() },
                                onMaxQuality = {},
                                visible = controlsVisible,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        if (!isPiPActive && !isFullscreen) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                item {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = currentStream.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = buildString {
                                                append(currentStream.uploader)
                                                currentStream.views?.let { append(" · ${formatViews(it)} views") }
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            ActionButton(
                                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                label = "Like",
                                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                onClick = { viewModel.toggleFavorite() }
                                            )
                                            ActionButton(
                                                icon = Icons.Default.Share,
                                                label = "Share",
                                                onClick = { viewModel.shareVideo(currentVideoId) }
                                            )
                                            ActionButton(
                                                icon = Icons.Default.Download,
                                                label = "Download",
                                                onClick = { showDownloadDialog = true }
                                            )
                                            // Re-asserts max quality on YouTube's player (same call
                                            // its own quality menu makes) — the unified-UI bridge.
                                            ActionButton(
                                                icon = Icons.Default.HighQuality,
                                                label = "Max HD",
                                                onClick = {}
                                            )
                                            ActionButton(
                                                icon = Icons.Default.PictureInPictureAlt,
                                                label = "PiP",
                                                onClick = {
                                                    val rational = Rational(16, 9)
                                                    val params = PictureInPictureParams.Builder()
                                                        .setAspectRatio(rational)
                                                        .build()
                                                    activity?.enterPictureInPictureMode(params)
                                                }
                                            )
                                            ActionButton(
                                                icon = Icons.Default.Lock,
                                                label = "Lock",
                                                onClick = { isTouchLocked = true }
                                            )
                                        }
                                    }
                                }

                                if (!currentStream.description.isNullOrBlank()) {
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Column(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Description",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = cleanDescription(currentStream.description),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                item {
                                    RelatedVideosSection(
                                        relatedVideos = relatedVideos,
                                        isLoading = isLoadingRelated,
                                        onVideoClick = { item ->
                                            onVideoLoaded(item.title, item.thumbnail)
                                            currentVideoId = item.videoId
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // ---- Audio-only mode: native ExoPlayer for background playback ----
                        AudioOnlyPlayer(
                            videoId = currentVideoId,
                            currentStream = currentStream,
                            viewModel = viewModel,
                            playbackSpeed = playbackSpeed,
                            skipSilence = skipSilence,
                            rememberPosition = rememberPosition,
                            isFavorite = isFavorite,
                            relatedVideos = relatedVideos,
                            isLoadingRelated = isLoadingRelated,
                            onBack = onBack,
                            onRelatedClick = { item ->
                                onVideoLoaded(item.title, item.thumbnail)
                                currentVideoId = item.videoId
                            }
                        )
                    }
                } ?: Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }

    // Touch lock: swallows every touch over the whole screen (incl. the WebView player)
    // until unlocked. Sits above all content in this Box.
    TouchLockOverlay(
        isLocked = isTouchLocked,
        onUnlock = { isTouchLocked = false }
    )

    if (showDownloadDialog) {
        stream?.let { s ->
            DownloadQualityDialog(
                onDismiss = { showDownloadDialog = false },
                onSelect = { quality ->
                    showDownloadDialog = false
                    viewModel.startDownload(
                        context = context,
                        videoId = currentVideoId,
                        title = s.title,
                        channelName = s.uploader,
                        thumbnailUrl = s.thumbnailUrl ?: "",
                        quality = quality
                    )
                }
            )
        }
    }
    } // end root Box
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun AudioOnlyPlayer(
    videoId: String,
    currentStream: com.freedomplay.app.domain.model.Stream,
    viewModel: PlayerViewModel,
    playbackSpeed: Float,
    skipSilence: Boolean,
    rememberPosition: Boolean,
    isFavorite: Boolean,
    relatedVideos: List<com.freedomplay.app.domain.model.StreamItem>,
    isLoadingRelated: Boolean,
    onBack: () -> Unit,
    onRelatedClick: (com.freedomplay.app.domain.model.StreamItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resumePosition by remember(videoId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(videoId) {
        resumePosition = viewModel.getSavedPosition(videoId)
    }

    val player = remember(videoId) {
        // Always pull the highest-bitrate audio rendition (matters for DASH/HLS manifests,
        // where multiple audio qualities exist — YouTube's ceiling is Opus ~160 kbps).
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .build()
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                playWhenReady = true
            }
    }

    // System media session — lock-screen / notification transport controls.
    DisposableEffect(player) {
        val session = androidx.media3.session.MediaSession.Builder(context, player)
            .setId("freedomplay_${videoId}_${System.currentTimeMillis()}")
            .build()
        onDispose { session.release() }
    }

    LaunchedEffect(currentStream, resumePosition) {
        val startAt = resumePosition ?: return@LaunchedEffect
        val keepPos = if (player.currentPosition > 0) player.currentPosition else startAt
        val source = PlaybackSourceFactory.build(
            context = context,
            stream = currentStream,
            selectedQuality = "Auto",
            audioOnly = true
        )
        if (source != null) {
            player.setMediaSource(source)
            player.prepare()
            if (keepPos > 0) player.seekTo(keepPos)
            player.playWhenReady = true
        }
    }

    LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(skipSilence) { player.skipSilenceEnabled = skipSilence }

    DisposableEffect(player) {
        onDispose {
            if (rememberPosition) {
                scope.launch { viewModel.savePosition(videoId, player.currentPosition) }
            }
            player.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Now Playing",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentStream.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        visibility = android.view.View.INVISIBLE
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = buildString {
                        append(currentStream.uploader)
                        currentStream.views?.let { append(" · ${formatViews(it)} views") }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = "Like",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = { viewModel.toggleFavorite() }
                    )
                    ActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = { viewModel.shareVideo(videoId) }
                    )
                    ActionButton(
                        icon = Icons.Default.Download,
                        label = "Download",
                        onClick = {
                            viewModel.startDownload(
                                context = context,
                                videoId = videoId,
                                title = currentStream.title,
                                channelName = currentStream.uploader,
                                thumbnailUrl = currentStream.thumbnailUrl ?: ""
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                VideoSeekBar(player = player)
                Spacer(modifier = Modifier.height(8.dp))
                PlaybackSpeedSelector(
                    playbackSpeed = playbackSpeed,
                    onSpeedSelected = { viewModel.setPlaybackSpeed(it) }
                )
            }

            item {
                RelatedVideosSection(
                    relatedVideos = relatedVideos,
                    isLoading = isLoadingRelated,
                    onVideoClick = onRelatedClick
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint
            )
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSeekBar(player: ExoPlayer) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        while (isActive) {
            if (player.isPlaying && !isDragging) {
                currentPosition = player.currentPosition.toFloat()
            }
            kotlinx.coroutines.delay(500)
        }
    }

    val duration = if (player.duration > 0) player.duration.toFloat() else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (isDragging) sliderPosition / duration else currentPosition / duration,
            onValueChange = { fraction ->
                isDragging = true
                sliderPosition = fraction * duration
            },
            onValueChangeFinished = {
                isDragging = false
                player.seekTo(sliderPosition.toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime((if (isDragging) sliderPosition else currentPosition).toLong()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                text = formatTime(duration.toLong()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlaybackSpeedSelector(
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Speed", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("${playbackSpeed}x", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                speeds.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text("${speed}x", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onSpeedSelected(speed)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    return TimeUtils.formatDuration(millis / 1000)
}

/** Descriptions arrive as HTML fragments — turn breaks into newlines and drop other tags. */
private fun cleanDescription(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#\\d+;")) { m ->
            m.value.removeSurrounding("&#", ";").toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}
