package com.freedomplay.app.presentation.ui.screens.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.freedomplay.app.presentation.ui.components.VideoCard
import com.freedomplay.app.presentation.ui.components.formatViews
import com.freedomplay.app.presentation.viewmodel.PlayerViewModel
import com.freedomplay.app.domain.model.StreamFormat

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
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    LaunchedEffect(stream) {
        stream?.let {
            onVideoLoaded(it.title, it.thumbnailUrl ?: "")
        }
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Now Playing",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF4081),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading video...",
                    color = Color(0xFF808080),
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
                            color = Color(0xFF808080),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadVideo(videoId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF4081)
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            stream != null -> {
                val player = remember(context) {
                    ExoPlayer.Builder(context).build().apply {
                        val bestStream = stream!!.videoStreams
                            .filter { it.url != null }
                            .maxByOrNull { it.height ?: 0 }
                        val streamUrl = bestStream?.url ?: stream!!.audioStreams.firstOrNull()?.url ?: ""

                        val mediaItem = MediaItem.Builder()
                            .setUri(streamUrl)
                            .build()
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                    }
                }

                DisposableEffect(player) {
                    onDispose {
                        player.release()
                    }
                }

                // Player view
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                setBackgroundColor(android.graphics.Color.BLACK)
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gesture overlay for seeking
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    val seekDelta = (dragAmount / 10).toLong() * 1000
                                    val newPos = player.currentPosition + seekDelta
                                    player.seekTo(newPos.coerceIn(0, player.duration))
                                }
                            }
                    )
                }

                // Video info and controls
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF000000))
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stream!!.title,
                            color = Color(0xFFE0E0E0),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append(stream!!.uploader)
                                stream!!.views?.let { append(" · ${formatViews(it)} views") }
                            },
                            color = Color(0xFF808080),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Action buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ActionButton(Icons.Default.FavoriteBorder, "Like")
                            ActionButton(Icons.AutoMirrored.Filled.QueueMusic, "Queue")
                            ActionButton(Icons.Default.Share, "Share")
                            ActionButton(Icons.Default.Download, "Download")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Seek bar
                        VideoSeekBar(player = player)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quality selector
                        QualitySelector(
                            streams = stream!!.videoStreams,
                            selectedQuality = selectedQuality,
                            onQualitySelected = { viewModel.selectQuality(it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // PiP button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                val activity = context as? androidx.activity.ComponentActivity
                                activity?.let { act ->
                                    if (act.isInPictureInPictureMode.not()) {
                                        act.enterPictureInPictureMode(
                                            android.app.PictureInPictureParams.Builder().build()
                                        )
                                    }
                                }
                            }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PictureInPictureAlt,
                                        contentDescription = "PiP",
                                        tint = Color(0xFFE0E0E0)
                                    )
                                    Text("PiP", color = Color(0xFF808080), fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = {
                                val activity = context as? androidx.activity.ComponentActivity
                                activity?.let {
                                    it.requestedOrientation = if (it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    else
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color(0xFFE0E0E0)
                                    )
                                    Text("Fullscreen", color = Color(0xFF808080), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Related videos section
                    if (stream!!.videoStreams.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Related",
                                color = Color(0xFFE0E0E0),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(stream!!.videoStreams.take(10)) { related ->
                            RelatedVideoItem(
                                title = related.quality ?: "Unknown",
                                subtitle = related.mimeType ?: ""
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { }) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color(0xFFE0E0E0)
            )
        }
        Text(label, color = Color(0xFF808080), fontSize = 12.sp)
    }
}

@Composable
private fun VideoSeekBar(player: ExoPlayer) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val position = if (isDragging) sliderPosition else player.currentPosition.toFloat()
    val duration = if (player.duration > 0) player.duration.toFloat() else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = position / duration,
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
                thumbColor = Color(0xFFFF4081),
                activeTrackColor = Color(0xFFFF4081),
                inactiveTrackColor = Color(0xFF2A2A3E)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(position.toLong()),
                color = Color(0xFF808080),
                fontSize = 12.sp
            )
            Text(
                text = formatTime(duration.toLong()),
                color = Color(0xFF808080),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun QualitySelector(
    streams: List<StreamFormat>,
    selectedQuality: String,
    onQualitySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val qualities = streams
        .mapNotNull { it.quality }
        .distinct()
        .sortedBy { q ->
            when {
                q.contains("1440") -> 6
                q.contains("1080") -> 5
                q.contains("720") -> 4
                q.contains("480") -> 3
                q.contains("360") -> 2
                q.contains("240") -> 1
                q.contains("144") -> 0
                else -> 0
            }
        }
        .reversed()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Quality", color = Color(0xFFE0E0E0), fontSize = 14.sp)
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A1A2E)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(selectedQuality, color = Color(0xFFE0E0E0), fontSize = 13.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1A1A2E))
            ) {
                listOf("Auto") + qualities.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(quality, color = Color(0xFFE0E0E0)) },
                        onClick = {
                            onQualitySelected(quality)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoItem(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFFE0E0E0),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color(0xFF808080),
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
