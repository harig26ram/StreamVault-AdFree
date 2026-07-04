package com.streamvault.app.presentation.ui.screen

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.auth.AuthState
import com.streamvault.app.domain.model.FeedItem
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
    authManager: AuthManager = hiltViewModel<com.streamvault.app.presentation.viewmodel.SplashViewModel>().getAuthManager()
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by authManager.authState.collectAsState()
    val userProfile by authManager.userProfile.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

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
        // Top bar - respects system insets for notification bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "StreamVault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    if (authState is AuthState.Authenticated && userProfile?.photoUrl != null) {
                        AsyncImage(
                            model = userProfile?.photoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(28.dp)
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
            windowInsets = TopAppBarDefaults.windowInsets
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
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
                            }
                            is FeedItem.Playlist -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlaylistClick(feedItem.playlist.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(120.dp, 68.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        ) {
                                            AsyncImage(
                                                model = feedItem.playlist.thumbnailUrl,
                                                contentDescription = feedItem.playlist.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.Center),
                                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlaylistPlay,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(4.dp),
                                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = feedItem.playlist.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
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
                                        .clickable { onChannelClick(feedItem.channel.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(0.dp)
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
                                                .size(48.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = feedItem.channel.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
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
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
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
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}