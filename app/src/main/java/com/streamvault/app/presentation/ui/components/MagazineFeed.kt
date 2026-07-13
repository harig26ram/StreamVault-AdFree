package com.streamvault.app.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.Video

@Composable
fun MagazineFeed(
    feedItems: List<FeedItem>,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val videos = feedItems.filterIsInstance<FeedItem.Video>().map { it.video }
    
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Featured item - full width
        if (videos.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                CompactVideoCard(
                    video = videos.first(),
                    onClick = { onVideoClick(videos.first().id) },
                    featured = true
                )
            }
        }
        
        // Remaining videos in 2-column grid
        items(
            items = videos.drop(1),
            key = { it.id }
        ) { video ->
            CompactVideoCard(
                video = video,
                onClick = { onVideoClick(video.id) },
                featured = false
            )
        }
    }
}

@Composable
private fun CompactVideoCard(
    video: Video,
    onClick: () -> Unit,
    featured: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(if (featured) Modifier.fillMaxWidth() else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .then(if (featured) Modifier.fillMaxWidth() else Modifier.fillMaxWidth())
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Duration badge
            if (video.duration.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = video.duration,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Compact detail row
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel avatar (small)
            AsyncImage(
                model = video.channelAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(if (featured) 32.dp else 24.dp)
                    .clip(RoundedCornerShape(if (featured) 16.dp else 12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (featured) 13.sp else 11.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (featured) 16.sp else 14.sp
                )
                Text(
                    text = buildString {
                        append(video.channelName)
                        if (video.viewCount.isNotBlank()) append(" · ${video.viewCount}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (featured) 11.sp else 10.sp
                )
            }
        }
    }
}
