package com.streamvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.theme.*

data class SongItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val songs = remember {
        listOf(
            SongItem("1", "Blinding Lights", "The Weeknd", "After Hours", "3:20", Icons.Filled.Favorite),
            SongItem("2", "Shape of You", "Ed Sheeran", "Divide", "3:53", Icons.Filled.MusicNote),
            SongItem("3", "Dance Monkey", "Tones and I", "The Kids Are Coming", "3:29", Icons.Filled.Mood),
            SongItem("4", "Someone You Loved", "Lewis Capaldi", "Divinely Uninspired", "3:02", Icons.Filled.FavoriteBorder),
            SongItem("5", "Watermelon Sugar", "Harry Styles", "Fine Line", "2:54", Icons.Filled.WaterDrop),
            SongItem("6", "Levitating", "Dua Lipa", "Future Nostalgia", "3:23", Icons.Filled.Star),
            SongItem("7", "Stay", "The Kid LAROI", "F*CK LOVE 3", "2:21", Icons.Filled.MusicNote),
            SongItem("8", "Industry Baby", "Lil Nas X", "Montero", "3:32", Icons.Filled.EmojiEvents)
        )
    }

    var currentlyPlaying by remember { mutableStateOf<SongItem?>(null) }

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
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "YouTube Music",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Filled.Cast,
                        contentDescription = "Cast",
                        tint = TextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground
            )
        )

        // Now Playing Card
        currentlyPlaying?.let { song ->
            NowPlayingCard(
                song = song,
                onPause = { currentlyPlaying = null }
            )
        }

        // Song List
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(songs) { song ->
                SongCard(
                    song = song,
                    isPlaying = currentlyPlaying?.id == song.id,
                    onClick = { currentlyPlaying = song }
                )
            }
        }
    }
}

@Composable
fun NowPlayingCard(
    song: SongItem,
    onPause: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Accent.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Accent, Secondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now Playing",
                    fontSize = 12.sp,
                    color = Accent
                )
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = song.artist,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onPause) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = "Pause",
                    tint = Accent
                )
            }
        }
    }
}

@Composable
fun SongCard(
    song: SongItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) Accent.copy(alpha = 0.1f) else DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (isPlaying) listOf(Accent, Secondary) else listOf(Primary, DarkCard)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = song.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (isPlaying) 1f else 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlaying) Accent else TextPrimary
                )
                Text(
                    text = song.artist,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            Text(
                text = song.duration,
                fontSize = 12.sp,
                color = TextMuted
            )
            if (isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Equalizer,
                    contentDescription = null,
                    tint = Accent
                )
            }
        }
    }
}
