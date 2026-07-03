package com.streamvault.app.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.presentation.viewmodel.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Shorts", "Live", "Playlists")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text(uiState.channel?.name ?: "Channel") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications"
                    )
                }
            }
        )

        uiState.channel?.let { channel ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(8.dp),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (channel.subscriberCount.contains("subscriber")) channel.subscriberCount else "${channel.subscriberCount} subscribers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (uiState.isSubscribed) {
                                viewModel.unsubscribe()
                            } else {
                                viewModel.subscribe()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isSubscribed) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(if (uiState.isSubscribed) "Subscribed" else "Subscribe")
                    }

                    OutlinedButton(onClick = { }) {
                        Text("Join")
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val filteredVideos = when (selectedTab) {
                0 -> uiState.videos.filterIsInstance<FeedItem.Video>()
                1 -> uiState.videos.filterIsInstance<FeedItem.Video>().filter { it.video.isShort }
                2 -> uiState.videos.filterIsInstance<FeedItem.Video>().filter { it.video.isLive }
                else -> emptyList()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredVideos.size) { index ->
                    val feedItem = filteredVideos[index]
                    when (feedItem) {
                        is FeedItem.Video -> {
                            com.streamvault.app.presentation.ui.components.VideoCard(
                                video = feedItem.video,
                                onClick = { onVideoClick(feedItem.video.id) }
                            )
                        }
                        else -> {}
                    }
                }

                if (filteredVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No ${tabs[selectedTab].lowercase()} found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}