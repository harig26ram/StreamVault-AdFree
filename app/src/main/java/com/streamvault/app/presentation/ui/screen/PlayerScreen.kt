package com.streamvault.app.presentation.ui.screen

import android.app.PictureInPictureParams
import android.graphics.SurfaceTexture
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.model.VideoFormat
import com.streamvault.app.presentation.viewmodel.PlayerViewModel
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
            viewModel.loadFormats(currentVideoId)
            viewModel.loadCaptions(currentVideoId)
            viewModel.loadComments(currentVideoId)
            if (viewModel.sponsorBlockEnabled) {
                viewModel.loadSponsorSegments(currentVideoId)
            }
        }
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
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                Box(modifier = Modifier.fillMaxSize()) {
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

                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = { showCaptionSheet = true },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
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

                            Surface(
                                onClick = { showQualitySheet = true },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.qualityLabel,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (uiState.playbackSpeed == 1f) "1x" else "${uiState.playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

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
                                viewModel.seekTo((uiState.position - 10000).coerceAtLeast(0))
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, "Rewind", tint = Color.White, modifier = Modifier.size(30.dp))
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
                            Icon(Icons.Default.Forward30, "Forward", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

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
                                .height(28.dp)
                                .padding(bottom = 4.dp)
                        )
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
                            label = "Like",
                            onClick = {
                                android.widget.Toast.makeText(context, "Liked!", android.widget.Toast.LENGTH_SHORT).show()
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
                            label = "Download",
                            onClick = {
                                android.widget.Toast.makeText(context, "Downloads coming soon", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
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
                        val text = connection.getInputStream().bufferedReader().readText()
                        captionText = parseCaptions(text)
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
