package com.streamvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.theme.*

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val duration: String,
    val views: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val videos = remember {
        listOf(
            VideoItem("1", "Chill Vibes Mix 2024", "Music Channel", "", "2:45:00", "1.2M views", Icons.Filled.MusicNote),
            VideoItem("2", "Coding Tutorial", "Dev Academy", "", "1:23:00", "500K views", Icons.Filled.Code),
            VideoItem("3", "Travel Vlog Japan", "Wanderlust", "", "45:00", "800K views", Icons.Filled.Flight),
            VideoItem("4", "Cooking Masterclass", "Chef's Kitchen", "", "32:00", "1.5M views", Icons.Filled.Restaurant),
            VideoItem("5", "Workout Routine", "Fitness Pro", "", "28:00", "2M views", Icons.Filled.FitnessCenter),
            VideoItem("6", "Tech Review 2024", "Tech Insider", "", "18:00", "3M views", Icons.Filled.Memory),
            VideoItem("7", "Gaming Highlights", "Game Zone", "", "15:00", "4.5M views", Icons.Filled.SportsEsports),
            VideoItem("8", "Meditation Guide", "Calm Mind", "", "60:00", "900K views", Icons.Filled.SelfImprovement)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "StreamVault",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = "Profile",
                        tint = TextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground
            )
        )

        // Content
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                VideoCard(video = video)
            }
        }
    }
}

@Composable
fun VideoCard(video: VideoItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Primary, Secondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = video.icon,
                    contentDescription = null,
                    tint = Accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
                // Duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.8f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.duration,
                        fontSize = 10.sp,
                        color = TextPrimary
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channel,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = video.views,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
