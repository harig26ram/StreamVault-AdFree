package com.streamvault.app.presentation.ui.screen

import android.app.PictureInPictureParams
import android.net.Uri
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.streamvault.app.domain.model.Video
import com.streamvault.app.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentVideoId by viewModel.currentVideoId.collectAsState()
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var volume by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var showVolumeIcon by remember { mutableStateOf(false) }
    var showBrightnessIcon by remember { mutableStateOf(false) }
    var volumeIcon by remember { mutableStateOf(Icons.Default.VolumeUp) }
    var seekDelta by remember { mutableLongStateOf(0L) }
    var showSeekIndicator by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    val accentBlue = MaterialTheme.colorScheme.primary
    val dimGray = MaterialTheme.colorScheme.onSurfaceVariant

    // ExoPlayer — use Activity context for SurfaceView compatibility
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Proper lifecycle management — release player when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) { }
        }
    }

    // Screen brightness — safely manage window attributes
    DisposableEffect(Unit) {
        val act = context as? ComponentActivity
        val window = act?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        try {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) { }
        onDispose {
            try {
                val lp = window?.attributes
                if (lp != null && originalBrightness >= 0f) {
                    lp.screenBrightness = originalBrightness
                    window?.attributes = lp
                }
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) { }
        }
    }

    // Player event listener — with proper error handling
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration
                    playerError = null
                }
                if (state == Player.STATE_ENDED) {
                    viewModel.playNextVideo()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.message ?: "Playback error occurred"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // SponsorBlock auto-skip
    LaunchedEffect(uiState.sponsorSegments, isPlaying) {
        if (uiState.sponsorSegments.isNotEmpty() && isPlaying) {
            while (isActive) {
                try {
                    val pos = exoPlayer.currentPosition / 1000.0
                    val inSegment = uiState.sponsorSegments.any { seg ->
                        val start = seg.segment?.getOrNull(0) ?: 0.0
                        val end = seg.segment?.getOrNull(1) ?: 0.0
                        pos in start..end
                    }
                    if (inSegment) {
                        val skipTo = uiState.sponsorSegments
                            .filter { seg ->
                                val start = seg.segment?.getOrNull(0) ?: 0.0
                                val end = seg.segment?.getOrNull(1) ?: 0.0
                                pos in start..end
                            }
                            .maxOfOrNull { (it.segment?.getOrNull(1) ?: 0.0) } ?: 0.0
                        exoPlayer.seekTo((skipTo * 1000).toLong())
                    }
                } catch (_: Exception) { }
                delay(500)
            }
        }
    }

    // Load video when URL changes
    LaunchedEffect(uiState.videoUrl) {
        uiState.videoUrl?.let { url ->
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                playbackPosition = 0
                duration = 0
                playerError = null
            } catch (e: Exception) {
                playerError = "Failed to load video: ${e.message}"
                isBuffering = false
            }
        }
    }

    // Load video metadata on video change
    LaunchedEffect(currentVideoId) {
        if (currentVideoId.isNotBlank()) {
            viewModel.loadFormats(currentVideoId)
            viewModel.loadCaptions(currentVideoId)
            viewModel.loadComments(currentVideoId)
            if (viewModel.sponsorBlockEnabled) {
                viewModel.loadSponsorSegments(currentVideoId)
            }
        }
    }

    // Position polling — reduced frequency to 500ms for performance
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            try {
                playbackPosition = exoPlayer.currentPosition
            } catch (_: Exception) { }
            delay(500)
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    // Brightness control
    LaunchedEffect(brightness) {
        try {
            val act = context as? ComponentActivity
            val window = act?.window
            val lp = window?.attributes
            if (lp != null) {
                lp.screenBrightness = brightness
                window?.attributes = lp
            }
        } catch (_: Exception) { }
    }

    // Playback speed
    LaunchedEffect(playbackSpeed) {
        try {
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        } catch (_: Exception) { }
    }

    // Volume icon auto-hide
    LaunchedEffect(volumeIcon, showVolumeIcon) {
        if (showVolumeIcon) {
            delay(1000)
            showVolumeIcon = false
        }
    }

    // Seek indicator auto-hide
    LaunchedEffect(showSeekIndicator) {
        if (showSeekIndicator) {
            delay(800)
            showSeekIndicator = false
            seekDelta = 0
        }
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = floor(ms / 1000.0).toLong()
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    fun formatCompactViewCount(text: String): String {
        return text.replace(Regex("\\(\\d+\\)"), "").trim()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Video player view
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // Gesture overlay — tap to toggle controls, drag for volume/brightness
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val boxWidth = size.width.toFloat()
                                if (offset.x < boxWidth / 2) {
                                    showBrightnessIcon = true
                                } else {
                                    showVolumeIcon = true
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val boxWidth = size.width.toFloat()
                                val boxHeight = size.height.toFloat()

                                if (change.position.x < boxWidth / 2) {
                                    brightness = (brightness - dragAmount.y / boxHeight * 1.5f).coerceIn(0.01f, 1f)
                                    showBrightnessIcon = true
                                } else {
                                    volume = (volume - dragAmount.y / boxHeight * 1.5f).coerceIn(0f, 1f)
                                    showVolumeIcon = true
                                    volumeIcon = when {
                                        volume == 0f -> Icons.Default.VolumeOff
                                        volume < 0.5f -> Icons.Default.VolumeDown
                                        else -> Icons.Default.VolumeUp
                                    }
                                    exoPlayer.volume = volume
                                }
                            },
                            onDragEnd = {
                                showVolumeIcon = false
                                showBrightnessIcon = false
                            }
                        )
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showControls = !showControls }
            )

            // Player controls overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top bar — Back button | Channel info | Settings row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Channel name in center
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 60.dp)
                                .clickable { uiState.video?.let { onChannelClick(it.channelId) } },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            uiState.video?.let { video ->
                                Text(
                                    text = video.channelName,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Settings row — right side
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Audio only toggle
                            Surface(
                                onClick = {
                                    isAudioOnly = !isAudioOnly
                                    if (isAudioOnly) {
                                        val bestAudio = uiState.formats
                                            .filter { it.isAudio }
                                            .maxByOrNull { it.bitrate }
                                        if (bestAudio != null) {
                                            viewModel.setQuality(bestAudio)
                                        }
                                    } else {
                                        viewModel.setQualityAuto()
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = if (isAudioOnly) accentBlue.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isAudioOnly) Icons.Default.MusicNote else Icons.Default.MusicOff,
                                        contentDescription = "Audio only",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // CC toggle
                            Surface(
                                onClick = { showCaptionSheet = true },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = if (captionsEnabled) accentBlue.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "CC",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Quality
                            Surface(
                                onClick = { showQualitySheet = true },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.quality,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Speed
                            Surface(
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Seek indicator
                    if (showSeekIndicator) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                .align(if (seekDirection >= 0) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (seekDirection >= 0) Icons.Default.Forward30 else Icons.Default.Replay10,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Center playback controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                seekDelta = -10000
                                seekDirection = -1
                                showSeekIndicator = true
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, "Rewind", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 30000).coerceAtMost(duration))
                                seekDelta = 30000
                                seekDirection = 1
                                showSeekIndicator = true
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward30, "Forward", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    // Bottom seek bar with time labels
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(playbackPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                formatTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = playbackPosition.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { playbackPosition = it.toLong() },
                            onValueChangeFinished = {
                                try { exoPlayer.seekTo(playbackPosition) } catch (_: Exception) { }
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = accentBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .height(28.dp)
                                .padding(bottom = 4.dp)
                        )
                    }

                    // PiP button — top right corner, below settings
                    IconButton(
                        onClick = {
                            try {
                                val act = context as? ComponentActivity
                                act?.let {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    it.enterPictureInPictureMode(params)
                                }
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 8.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.PictureInPictureAlt,
                            "Picture in Picture",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Volume indicator
            if (showVolumeIcon) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp)
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(volumeIcon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text("${(volume * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Brightness indicator
            if (showBrightnessIcon) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp)
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Brightness6, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text("${(brightness * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Buffering indicator
            if (isBuffering && !showControls) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp).align(Alignment.Center),
                    color = accentBlue,
                    strokeWidth = 3.dp
                )
            }

            // Error state with retry
            playerError?.let { error ->
                if (!isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable {
                                playerError = null
                                isBuffering = true
                                uiState.videoUrl?.let { url ->
                                    try {
                                        val mediaItem = MediaItem.fromUri(Uri.parse(url))
                                        exoPlayer.setMediaItem(mediaItem)
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                    } catch (e: Exception) {
                                        playerError = "Retry failed: ${e.message}"
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = error,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Text(
                                text = "Tap to retry",
                                color = accentBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Content below player
        val videoPlayerHeightPx = context.resources.displayMetrics.widthPixels * 9f / 16f
        val videoPlayerHeightDp = (videoPlayerHeightPx / context.resources.displayMetrics.density).toInt().coerceAtLeast(200).dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = videoPlayerHeightDp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item(key = "video_info") {
                uiState.video?.let { video ->
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (video.viewCount.isNotBlank()) {
                                Text(text = formatCompactViewCount(video.viewCount), fontSize = 12.sp, color = dimGray)
                            }
                            if (video.publishedTime.isNotBlank()) {
                                Text("· ${video.publishedTime}", fontSize = 12.sp, color = dimGray)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = video.channelAvatar,
                            contentDescription = video.channelName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onChannelClick(video.channelId) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onChannelClick(video.channelId) }
                        )
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Subscribe", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniAction(icon = Icons.Default.ThumbUp, label = "Like")
                        MiniAction(icon = Icons.Default.Share, label = "Share")
                        MiniAction(icon = Icons.Default.PlaylistAdd, label = "Save")
                        MiniAction(icon = Icons.Default.Download, label = "Download")
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    if (video.description.isNotBlank()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Description", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = dimGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = video.description,
                                fontSize = 12.sp,
                                color = dimGray.copy(alpha = 0.8f),
                                lineHeight = 18.sp,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            if (uiState.relatedVideos.isNotEmpty()) {
                item(key = "up_next") {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = uiState.relatedVideos.filter { it.id.isNotBlank() },
                    key = { it.id }
                ) { video ->
                    RelatedVideoCard(
                        video = video,
                        onClick = { viewModel.loadVideo(video.id) }
                    )
                }
            }

            if (uiState.comments.isNotEmpty()) {
                item(key = "comments_header") {
                    Text(
                        text = "Comments (${uiState.comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = uiState.comments,
                    key = { "${it.authorName}_${it.content.take(20)}" }
                ) { comment ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = comment.authorName.take(1).uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = comment.authorName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = comment.publishedTime,
                                    fontSize = 10.sp,
                                    color = dimGray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = comment.content,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        if (comment.voteCount.isNotBlank()) {
                            Text(
                                text = comment.voteCount,
                                fontSize = 11.sp,
                                color = dimGray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Speed bottom sheet
    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Playback Speed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                speedOptions.forEach { speed ->
                    val label = if (speed == 1f) "Normal" else "${speed}x"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (playbackSpeed == speed) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                playbackSpeed = speed
                                showSpeedSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 15.sp)
                        if (playbackSpeed == speed) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Quality bottom sheet
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Video Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val videoFormats = uiState.formats.filter { it.isVideo }.sortedByDescending { it.height ?: 0 }
                val audioFormats = uiState.formats.filter { it.isAudio }.sortedByDescending { it.bitrate }

                if (videoFormats.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (uiState.selectedFormat == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.setQualityAuto()
                                showQualitySheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto (Recommended)", fontSize = 15.sp)
                        if (uiState.selectedFormat == null) {
                            Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (videoFormats.isNotEmpty()) {
                    Text("Video", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                videoFormats.forEach { fmt ->
                    val label = fmt.displayLabel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (uiState.selectedFormat?.itag == fmt.itag) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.setQuality(fmt)
                                showQualitySheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 15.sp)
                        if (uiState.selectedFormat?.itag == fmt.itag) {
                            Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (audioFormats.isNotEmpty()) {
                    Text("Audio Only", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                audioFormats.forEach { fmt ->
                    val label = fmt.displayLabel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (uiState.selectedFormat?.itag == fmt.itag) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.setQuality(fmt)
                                showQualitySheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 15.sp)
                        if (uiState.selectedFormat?.itag == fmt.itag) {
                            Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Caption bottom sheet
    if (showCaptionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCaptionSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Subtitles / CC",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (!captionsEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable {
                            captionsEnabled = false
                            showCaptionSheet = false
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Off", fontSize = 15.sp)
                    if (!captionsEnabled) {
                        Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }

                uiState.captionTracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (captionsEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                captionsEnabled = true
                                showCaptionSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(track.name, fontSize = 15.sp)
                        if (captionsEnabled) {
                            Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RelatedVideoCard(
    video: Video,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.channelName.isNotBlank()) {
                Text(
                    text = video.channelName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (video.viewCount.isNotBlank()) {
                    Text(text = video.viewCount, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (video.publishedTime.isNotBlank()) {
                    Text(text = video.publishedTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
