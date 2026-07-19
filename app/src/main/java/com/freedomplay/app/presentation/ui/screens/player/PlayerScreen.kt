package com.freedomplay.app.presentation.ui.screens.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.freedomplay.app.presentation.ui.components.formatViews
import com.freedomplay.app.presentation.viewmodel.PlayerViewModel
import com.freedomplay.app.domain.model.StreamFormat
import com.freedomplay.app.util.TimeUtils
import android.os.Build
import androidx.activity.ComponentActivity
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val audioOnly by viewModel.audioOnly.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val rememberPosition by viewModel.rememberPosition.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    LaunchedEffect(stream) {
        stream?.let {
            onVideoLoaded(it.title, it.thumbnailUrl ?: "")
        }
    }

    val context = LocalContext.current
    var isPiPActive by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isPiPActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
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
                            onClick = { viewModel.loadVideo(videoId) },
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
                    var savedPosition by remember { mutableStateOf(0L) }

                    LaunchedEffect(Unit) {
                        savedPosition = viewModel.getSavedPosition(videoId)
                    }

                    val player = remember(videoId) {
                        ExoPlayer.Builder(context).build().apply {
                            val bestStream = if (selectedQuality == "Auto") {
                                currentStream.videoStreams
                                    .filter { it.url != null }
                                    .maxByOrNull { it.height ?: 0 }
                            } else {
                                currentStream.videoStreams
                                    .filter { it.quality == selectedQuality && it.url != null }
                                    .maxByOrNull { it.height ?: 0 }
                            }
                            val streamUrl = bestStream?.url
                                ?: currentStream.audioStreams.firstOrNull()?.url
                                ?: ""

                            val mediaItem = MediaItem.Builder()
                                .setUri(streamUrl)
                                .build()
                            setMediaItem(mediaItem)
                            prepare()
                            playWhenReady = true
                            if (savedPosition > 0) {
                                seekTo(savedPosition)
                            }
                        }
                    }

                    LaunchedEffect(selectedQuality, currentStream) {
                        if (selectedQuality != "Auto") {
                            val targetStream = currentStream.videoStreams
                                .filter { it.quality == selectedQuality && it.url != null }
                                .maxByOrNull { it.height ?: 0 }
                            targetStream?.url?.let { url ->
                                val wasPlaying = player.isPlaying
                                val pos = player.currentPosition
                                player.setMediaItem(MediaItem.fromUri(url))
                                player.prepare()
                                player.seekTo(pos)
                                player.playWhenReady = wasPlaying
                            }
                        }
                    }

                    LaunchedEffect(playbackSpeed) {
                        player.setPlaybackSpeed(playbackSpeed)
                    }

                    LaunchedEffect(skipSilence) {
                        player.skipSilenceEnabled = skipSilence
                    }

                    DisposableEffect(player) {
                        onDispose {
                            if (rememberPosition) {
                                scope.launch {
                                    viewModel.savePosition(videoId, player.currentPosition)
                                }
                            }
                            player.release()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        if (audioOnly) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Speed,
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
                        } else {
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
                        }
                    }

                    if (!isPiPActive) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = currentStream.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
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
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "https://youtube.com/watch?v=$videoId")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                                    }
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

                            VideoSeekBar(player = player, videoId = videoId, viewModel = viewModel)

                            Spacer(modifier = Modifier.height(16.dp))

                            QualitySelector(
                                streams = currentStream.videoStreams,
                                selectedQuality = selectedQuality,
                                onQualitySelected = { viewModel.selectQuality(it) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            PlaybackSpeedSelector(
                                playbackSpeed = playbackSpeed,
                                onSpeedSelected = { viewModel.setPlaybackSpeed(it) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = {
                                    val activity = context as? ComponentActivity
                                    activity?.let { act ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act.isInPictureInPictureMode.not()) {
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
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text("PiP", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                                IconButton(onClick = {
                                    val activity = context as? ComponentActivity
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
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text("Fullscreen", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (!currentStream.description.isNullOrBlank()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    modifier = Modifier
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
                                        text = currentStream.description ?: "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if ((currentStream.description?.length ?: 0) > 100) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isDescriptionExpanded) "Show less" else "Show more",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        if (currentStream.videoStreams.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Available Qualities",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(currentStream.videoStreams.filter { it.quality != null }.distinctBy { it.quality }) { streamFormat ->
                                QualityItem(
                                    quality = streamFormat.quality ?: "Unknown",
                                    mimeType = streamFormat.mimeType ?: "",
                                    isSelected = selectedQuality == streamFormat.quality,
                                    onClick = { viewModel.selectQuality(streamFormat.quality ?: "Auto") }
                                )
                            }
                        }
                    }
                    } // end PiP guard
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

@Composable
private fun VideoSeekBar(player: ExoPlayer, videoId: String, viewModel: PlayerViewModel) {
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
        Text("Quality", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(selectedQuality, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Auto", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onQualitySelected("Auto")
                        expanded = false
                    }
                )
                qualities.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(quality, color = MaterialTheme.colorScheme.onSurface) },
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
private fun QualityItem(
    quality: String,
    mimeType: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(quality, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(mimeType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}


private fun formatTime(millis: Long): String {
    return TimeUtils.formatDuration(millis / 1000)
}
