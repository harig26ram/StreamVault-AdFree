package com.streamvault.app.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authManager: AuthManager = hiltViewModel<com.streamvault.app.presentation.viewmodel.SplashViewModel>().getAuthManager()
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by authManager.authState.collectAsState()
    val userProfile by authManager.userProfile.collectAsState()
    val listState = rememberLazyListState()

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
                                    onClick = { onVideoClick(feedItem.video.id) }
                                )
                            }
                            is FeedItem.Playlist -> {
                                // Playlist card
                            }
                            is FeedItem.Channel -> {
                                // Channel card
                            }
                            is FeedItem.CarouselItem -> {
                                // Carousel
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