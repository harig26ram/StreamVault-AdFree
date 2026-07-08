package com.streamvault.app.presentation.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.streamvault.app.R
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.auth.AuthState
import com.streamvault.app.cast.CastPlayer
import com.streamvault.app.cast.CastSessionManager
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.presentation.ui.components.CastDialog
import com.streamvault.app.presentation.ui.components.CastIconButton
import com.streamvault.app.presentation.ui.components.LoadingIndicator
import com.streamvault.app.presentation.ui.components.VideoCard
import com.streamvault.app.presentation.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authManager: AuthManager = hiltViewModel<com.streamvault.app.presentation.viewmodel.SplashViewModel>().getAuthManager(),
    castSessionManager: CastSessionManager = hiltViewModel<com.streamvault.app.presentation.viewmodel.SplashViewModel>().getCastSessionManager(),
    castPlayer: CastPlayer = hiltViewModel<com.streamvault.app.presentation.viewmodel.SplashViewModel>().getCastPlayer()
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by authManager.authState.collectAsState()
    val userProfile by authManager.userProfile.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val isCastConnected by castSessionManager.isConnected.collectAsState()
    val castDeviceName by castSessionManager.currentDeviceName.collectAsState()
    val isCastAvailable by castSessionManager.isAvailable.collectAsState()
    var showCastDialog by remember { mutableStateOf(false) }

    // Load more when scrolling to bottom
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !uiState.isLoading) {
                viewModel.loadMore()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Minimal top bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                )
            },
            actions = {
                if (isCastAvailable) {
                    CastIconButton(
                        isConnected = isCastConnected,
                        onClick = { showCastDialog = true }
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    if (authState is AuthState.Authenticated && userProfile?.photoUrl != null) {
                        AsyncImage(
                            model = userProfile?.photoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Account",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        // Content
        when {
            uiState.isLoading && uiState.feedItems.isEmpty() -> {
                LoadingIndicator()
            }
            uiState.error != null && uiState.feedItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Retry", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            !uiState.isLoading && uiState.feedItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No content available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pull down to refresh or try again later",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Retry", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.feedItems,
                        key = { it.id }
                    ) { feedItem ->
                        when (feedItem) {
                            is FeedItem.Video -> {
                                VideoCard(
                                    video = feedItem.video,
                                    onClick = { onVideoClick(feedItem.video.id) },
                                    onSaveToWatchLater = { video -> viewModel.addToWatchLater(video) },
                                    onShare = { video ->
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, video.watchUrl)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                    }
                                )
                                // Subtle divider between videos
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 2.dp
                                    ),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    thickness = 0.5.dp
                                )
                            }
                            is FeedItem.Playlist -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlaylistClick(feedItem.playlist.id) }
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp, 56.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        ) {
                                            AsyncImage(
                                                model = feedItem.playlist.thumbnailUrl,
                                                contentDescription = feedItem.playlist.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Surface(
                                                modifier = Modifier.align(Alignment.Center),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlaylistPlay,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(4.dp),
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = feedItem.playlist.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${feedItem.playlist.videoCount} videos · ${feedItem.playlist.channelName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            is FeedItem.Channel -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChannelClick(feedItem.channel.id) }
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = feedItem.channel.avatarUrl,
                                            contentDescription = feedItem.channel.name,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = feedItem.channel.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${feedItem.channel.subscriberCount} subscribers",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            is FeedItem.CarouselItem -> {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    items(
                                        items = feedItem.items,
                                        key = { it.id }
                                    ) { video ->
                                        VideoCard(
                                            video = video,
                                            onClick = { onVideoClick(video.id) },
                                            modifier = Modifier.width(280.dp),
                                            onSaveToWatchLater = { v -> viewModel.addToWatchLater(v) },
                                            onShare = { v ->
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, v.watchUrl)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Loading more indicator
                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCastDialog && isCastAvailable) {
        CastDialog(
            isConnected = isCastConnected,
            deviceName = castDeviceName,
            onDismiss = { showCastDialog = false },
            onConnect = { castSessionManager.startSession() },
            onDisconnect = {
                castPlayer.stop()
                castSessionManager.stopSession()
            }
        )
    }
}
