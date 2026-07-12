package com.streamvault.app.presentation.ui.screen

import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.graphics.SurfaceTexture
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.streamvault.app.R
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.model.VideoFormat
import com.streamvault.app.presentation.ui.components.MiniPlayer
import com.streamvault.app.presentation.viewmodel.PlayerViewModel
import com.streamvault.app.presentation.viewmodel.RepeatMode
import com.streamvault.player.core.PlayerState
import com.streamvault.player.ui.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onEqualizerClick: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentVideoId by viewModel.currentVideoId.collectAsState()
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var captionText by remember { mutableStateOf<String?>(null) }
    var selectedCaptionTrack by remember { mutableStateOf<com.streamvault.app.domain.model.CaptionTrack?>(null) }

    val dimGray = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(uiState.playerState) {
        isPlaying = uiState.playerState == PlayerState.Playing
        if (uiState.playerState is PlayerState.Error) {
            playerError = (uiState.playerState as PlayerState.Error).message
        }
        if (uiState.playerState == PlayerState.Ended) {
            viewModel.playNextVideo()
        }
    }

    LaunchedEffect(uiState.isAudioOnly) {
        isAudioOnly = uiState.isAudioOnly
    }

    DisposableEffect(Unit) {
        val act = context as? ComponentActivity
        val window = act?.window
        try {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) { }
        onDispose {
            try {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    LaunchedEffect(uiState.brightness) {
        try {
            val act = context as? ComponentActivity
            val window = act?.window
            val lp = window?.attributes
            if (lp != null) {
                lp.screenBrightness = uiState.brightness
                window?.attributes = lp
            }
        } catch (_: Exception) { }
    }


    LaunchedEffect(uiState.showVolumeIndicator) {
        if (uiState.showVolumeIndicator) {
            delay(1000)
            viewModel.hideVolumeIndicator()
        }
    }

    LaunchedEffect(uiState.showBrightnessIndicator) {
        if (uiState.showBrightnessIndicator) {
            delay(1000)
            viewModel.hideBrightnessIndicator()
        }
    }

    LaunchedEffect(currentVideoId) {
        if (currentVideoId.isNotBlank()) {
            viewModel.loadCaptions(currentVideoId)
            viewModel.loadComments(currentVideoId)
            if (viewModel.sponsorBlockEnabled) {
                viewModel.loadSponsorSegments(currentVideoId)
            }
        }
    }

    val act = context as? ComponentActivity
    val activityInPip = act?.isInPictureInPictureMode ?: false
    LaunchedEffect(activityInPip) {
        if (activityInPip) {
            viewModel.enterPipMode()
            showControls = false
        } else {
            viewModel.exitPipMode()
        }
    }

    DisposableEffect(uiState.isFullscreen) {
        val window = act?.window
        val windowInsetsController = window?.let { WindowInsetsControllerCompat(it, act.window.decorView) }
        if (uiState.isFullscreen) {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            windowInsetsController?.let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (uiState.showResumeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResumeDialog() },
            title = { Text(stringResource(R.string.resume)) },
            text = {
                Text(stringResource(R.string.resume_from, formatTime(uiState.savedPositionMs)))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resumeFromSavedPosition() }) {
                    Text(stringResource(R.string.resume))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResumeDialog() }) {
                    Text(stringResource(R.string.start_over))
                }
            }
        )
    }

    if (uiState.showQueueSheet) {
        QueueBottomSheet(
            queue = uiState.queue,
            currentIndex = uiState.queueIndex,
            repeatMode = uiState.repeatMode,
            shuffleEnabled = uiState.shuffleEnabled,
            onItemSelected = { viewModel.playFromQueue(it) },
            onRemoveItem = { viewModel.removeFromQueue(it) },
            onClearQueue = { viewModel.clearQueue() },
            onShuffleToggle = { viewModel.toggleShuffle() },
            onRepeatCycle = { viewModel.cycleRepeatMode() },
            onDismiss = { viewModel.toggleQueueSheet() }
        )
    }

    if (uiState.isMiniPlayer && uiState.video != null) {
        MiniPlayer(
            title = uiState.video!!.title,
            channelName = uiState.video!!.channelName,
            thumbnailUrl = uiState.video!!.thumbnailUrl,
            isPlaying = uiState.playerState == PlayerState.Playing,
            progress = if (uiState.duration > 0) (uiState.position.toFloat() / uiState.duration.toFloat()).coerceIn(0f, 1f) else 0f,
            onClick = { viewModel.exitMiniPlayer() },
            onPlayPause = { viewModel.togglePlayPause() },
            onClose = {
                viewModel.exitMiniPlayer()
                viewModel.pause()
            },
            onQueueClick = { viewModel.toggleQueueSheet() },
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                                viewModel.setSurface(Surface(surfaceTexture))
                            }
                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                viewModel.setSurface(null)
                                return true
                            }
                            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier
                    .let { if (uiState.isFullscreen) it.fillMaxSize() else it.fillMaxWidth().aspectRatio(16f / 9f) }
            )

            GestureOverlay(
                onTap = { showControls = !showControls },
                onDoubleTap = { viewModel.togglePlayPause() },
                onSwipeLeft = { viewModel.seekTo(uiState.position + 30000) },
                onSwipeRight = { viewModel.seekTo((uiState.position - 10000).coerceAtLeast(0)) },
                onVerticalSwipe = { delta, side ->
                    if (side == GestureSide.LEFT) {
                        viewModel.onBrightnessChanged(uiState.brightness - delta / 500f)
                    } else {
                        viewModel.onVolumeChanged(uiState.volume - delta / 500f)
                    }
                },
                modifier = Modifier
                    .let { if (uiState.isFullscreen) it.fillMaxSize() else it.fillMaxWidth().aspectRatio(16f / 9f) }
            )

            if (captionText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 48.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Text(
                        text = captionText ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )
                }
            }

            if (showControls) {
                Box(
                    modifier = Modifier
                        .let { if (uiState.isFullscreen) it.fillMaxSize() else it.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.TopCenter) }
                ) {
                    // Top gradient bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.4f), Color.Transparent)
                                )
                            )
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (uiState.isMiniPlayerEnabled && uiState.playerState == PlayerState.Playing) {
                                    viewModel.enterMiniPlayer()
                                } else {
                                    onBack()
                                }
                            },
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

                        uiState.video?.let { video ->
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 52.dp, end = 160.dp)
                                    .clickable { onChannelClick(video.channelId) }
                            ) {
                                Text(
                                    text = video.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = video.channelName,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { showCaptionSheet = true },
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "CC",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                onClick = { showQualitySheet = true },
                                modifier = Modifier
                                    .height(34.dp)
                                    .widthIn(min = 34.dp)
                                    .padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(17.dp),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.qualityLabel,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (uiState.playbackSpeed == 1f) "1x" else "${uiState.playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Transport controls (center)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.seekTo((uiState.position - 10000).coerceAtLeast(0))
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
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
                                viewModel.seekTo((uiState.position + 30000).coerceAtMost(uiState.duration))
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward30, "Forward 30s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    // Seek bar (bottom) - thinner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
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
                                formatTime(uiState.position),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                formatTime(uiState.duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = if (isDragging) dragPosition else uiState.position.toFloat().coerceIn(0f, uiState.duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { dragPosition = it; isDragging = true },
                            onValueChangeFinished = {
                                viewModel.seekTo(dragPosition.toLong())
                                isDragging = false
                            },
                            valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .height(12.dp)
                                .padding(bottom = 4.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 84.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleFullscreen() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (uiState.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (uiState.isFullscreen) "Exit fullscreen" else "Fullscreen",
                                tint = if (uiState.isFullscreen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val act = context as? ComponentActivity
                                    act?.let {
                                        val pipActions = listOf(
                                            android.app.RemoteAction(
                                                android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                                                "Pause", "Pause", PendingIntent.getBroadcast(context, 10, Intent("com.streamvault.app.ACTION_PAUSE"), PendingIntent.FLAG_IMMUTABLE)
                                            ),
                                            android.app.RemoteAction(
                                                android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_play),
                                                "Play", "Play", PendingIntent.getBroadcast(context, 11, Intent("com.streamvault.app.ACTION_PLAY"), PendingIntent.FLAG_IMMUTABLE)
                                            )
                                        )
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .setActions(pipActions)
                                            .build()
                                        it.enterPictureInPictureMode(params)
                                    }
                                } catch (_: Exception) { }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                "Picture in Picture",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleQueueSheet() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.QueueMusic,
                                stringResource(R.string.queue),
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onEqualizerClick,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Equalizer,
                                stringResource(R.string.equalizer),
                                tint = if (uiState.equalizerEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.playerState == PlayerState.Buffering && !showControls) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp).align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }

            playerError?.let { error ->
                if (uiState.playerState !is PlayerState.Buffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable {
                                playerError = null
                                uiState.video?.let { viewModel.loadVideo(it.id) }
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
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        VolumeBrightnessIndicator(
            type = IndicatorType.VOLUME,
            value = uiState.volume,
            visible = uiState.showVolumeIndicator,
            modifier = Modifier.fillMaxSize()
        )

        VolumeBrightnessIndicator(
            type = IndicatorType.BRIGHTNESS,
            value = uiState.brightness,
            visible = uiState.showBrightnessIndicator,
            modifier = Modifier.fillMaxSize()
        )

        val videoPlayerHeightPx = context.resources.displayMetrics.widthPixels * 9f / 16f
        val videoPlayerHeightDp = (videoPlayerHeightPx / context.resources.displayMetrics.density).toInt().coerceAtLeast(200).dp

        if (!uiState.isFullscreen) {
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
                                Text("\u00B7 ${video.publishedTime}", fontSize = 12.sp, color = dimGray)
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
                            onClick = { viewModel.toggleSubscription() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                if (uiState.isSubscribed) "Subscribed" else "Subscribe",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniAction(
                            icon = Icons.Default.ThumbUp,
                            label = if (video.likeCount.isNotBlank()) "Like ${video.likeCount}" else "Like",
                            onClick = {
                                android.widget.Toast.makeText(context, "Liked!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        MiniAction(
                            icon = Icons.Default.ThumbDown,
                            label = "Dislike",
                            onClick = {
                                android.widget.Toast.makeText(context, "Disliked!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        MiniAction(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {
                                val videoId = uiState.video?.id ?: return@MiniAction
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "https://youtube.com/watch?v=$videoId")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                            }
                        )
                        MiniAction(
                            icon = Icons.Default.PlaylistAdd,
                            label = if (uiState.savedToWatchLater) "Saved" else "Save",
                            onClick = { viewModel.saveToWatchLater() }
                        )
                        MiniAction(
                            icon = Icons.Default.Download,
                            label = if (uiState.isDownloaded) "Downloaded" else if (uiState.isPaused) "Resume" else if (uiState.isDownloading) "${uiState.downloadProgress}%" else "Download",
                            onClick = {
                                when {
                                    uiState.isDownloaded -> {
                                        viewModel.deleteDownload()
                                        android.widget.Toast.makeText(context, "Download deleted", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    uiState.isPaused -> {
                                        viewModel.startDownload()
                                    }
                                    uiState.isDownloading -> {
                                        viewModel.pauseDownload()
                                    }
                                    else -> {
                                        viewModel.startDownload()
                                    }
                                }
                            }
                        )
                        MiniAction(
                            icon = Icons.Default.OpenInNew,
                            label = "YouTube",
                            onClick = {
                                val videoId = uiState.video?.id ?: return@MiniAction
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                                try {
                                    intent.setPackage("com.google.android.youtube")
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")))
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    if (video.description.isNotBlank()) {
                        var descriptionExpanded by remember { mutableStateOf(false) }
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
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (video.description.lines().size > 3 || video.description.length > 120) {
                                Text(
                                    text = if (descriptionExpanded) "Show less" else "Show more",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { descriptionExpanded = !descriptionExpanded }
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant                        )
                    }
                }

                }

                if (uiState.chapters.size >= 2) {
                    item(key = "chapters") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Chapters", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = dimGray)
                            Spacer(modifier = Modifier.height(6.dp))
                            uiState.chapters.forEach { chapter ->
                                val isCurrent = uiState.position in chapter.startTimeMs..
                                    (uiState.chapters.getOrNull(uiState.chapters.indexOf(chapter) + 1)?.startTimeMs ?: uiState.duration)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.seekTo(chapter.startTimeMs)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chapter.formattedTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else dimGray.copy(alpha = 0.7f),
                                        modifier = Modifier.width(52.dp)
                                    )
                                    Text(
                                        text = chapter.title,
                                        fontSize = 12.sp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else dimGray.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
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
    }

    PlaybackSpeedSheet(
        visible = showSpeedSheet,
        currentSpeed = uiState.playbackSpeed,
        onSpeedSelected = { speed ->
            viewModel.setPlaybackSpeed(speed)
            showSpeedSheet = false
        },
        onDismiss = { showSpeedSheet = false }
    )

    val playerFormats = uiState.formats.map { fmt ->
        PlayerFormat(
            itag = fmt.itag,
            label = fmt.displayLabel,
            resolution = if (fmt.height != null) "${fmt.height}p" else null,
            isAudioOnly = fmt.isAdaptive && fmt.isAudio,
            isVideoOnly = fmt.isAdaptive && fmt.isVideo,
            isSelected = uiState.selectedFormat?.itag == fmt.itag
        )
    }
    val currentPlayerFormat = playerFormats.firstOrNull { it.itag == uiState.selectedFormat?.itag }

    QualitySelectorSheet(
        visible = showQualitySheet,
        formats = playerFormats,
        currentFormat = currentPlayerFormat,
        onFormatSelected = { fmt ->
            val videoFmt = uiState.formats.firstOrNull { it.itag == fmt.itag }
            if (videoFmt != null) viewModel.setQuality(videoFmt)
            showQualitySheet = false
        },
        onDismiss = { showQualitySheet = false }
    )

    val playerCaptionTracks = uiState.captionTracks.map { track ->
        CaptionTrack(
            id = track.languageCode,
            language = track.name,
            label = track.name,
            isAutoGenerated = track.isTranslatable
        )
    }
    val selectedPlayerCaption = uiState.selectedCaption?.let { cap ->
        playerCaptionTracks.firstOrNull { it.id == cap.languageCode }
    }

    val captionCoroutineScope = rememberCoroutineScope()

    CaptionSelectorSheet(
        visible = showCaptionSheet,
        captions = playerCaptionTracks,
        selectedCaption = selectedPlayerCaption,
        onCaptionSelected = { track ->
            val appTrack = if (track != null) {
                uiState.captionTracks.firstOrNull { it.languageCode == track.id }
            } else null
            selectedCaptionTrack = appTrack
            if (appTrack == null) {
                captionText = null
            } else {
                captionCoroutineScope.launch(Dispatchers.IO) {
                    try {
                        val url = java.net.URL(appTrack.baseUrl)
                        val connection = url.openConnection()
                        connection.getInputStream().bufferedReader().use { reader ->
                            captionText = parseCaptions(reader.readText())
                        }
                    } catch (e: Exception) {
                        captionText = null
                    }
                }
            }
            showCaptionSheet = false
        },
        onDismiss = { showCaptionSheet = false }
    )
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

fun parseCaptions(vttContent: String): String {
    val lines = vttContent.lines()
    val textLines = mutableListOf<String>()
    var inBlock = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("WEBVTT") || trimmed.isEmpty()) {
            inBlock = false
            continue
        }
        if (trimmed.contains("-->")) {
            inBlock = true
            continue
        }
        if (inBlock && trimmed.isNotEmpty() && !trimmed.all { it.isDigit() || it == ':' || it == '.' || it == ',' }) {
            textLines.add(trimmed)
            inBlock = false
        }
    }
    return textLines.joinToString(" ")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    queue: List<com.streamvault.app.presentation.viewmodel.QueueItem>,
    currentIndex: Int,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onItemSelected: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatCycle: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_queue_confirm)) },
            text = { Text(stringResource(R.string.clear_queue_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearQueue()
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.up_next_queue, queue.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onShuffleToggle,
                    shape = RoundedCornerShape(20.dp),
                    color = if (shuffleEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            modifier = Modifier.size(16.dp),
                            tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.shuffle),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = onRepeatCycle,
                    shape = RoundedCornerShape(20.dp),
                    color = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (icon, desc) = when (repeatMode) {
                            RepeatMode.OFF -> Icons.Default.Repeat to stringResource(R.string.repeat_off)
                            RepeatMode.ONE -> Icons.Default.RepeatOne to stringResource(R.string.repeat_one)
                            RepeatMode.ALL -> Icons.Default.Repeat to stringResource(R.string.repeat_all)
                        }
                        Icon(
                            icon,
                            contentDescription = desc,
                            modifier = Modifier.size(16.dp),
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (repeatMode) {
                                RepeatMode.OFF -> stringResource(R.string.repeat_off)
                                RepeatMode.ONE -> stringResource(R.string.repeat_one)
                                RepeatMode.ALL -> stringResource(R.string.repeat_all)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (queue.isNotEmpty()) {
                    Surface(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_queue),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.clear_queue),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_queue_items),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    itemsIndexed(queue, key = { _, item -> item.videoId }) { index, item ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onItemSelected(index) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (isCurrent) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(2.dp),
                                        shape = RoundedCornerShape(3.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Icon(
                                            Icons.Default.Equalizer,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(1.dp)
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.channelName.isNotBlank()) {
                                    Text(
                                        text = item.channelName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveItem(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove_from_queue),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
