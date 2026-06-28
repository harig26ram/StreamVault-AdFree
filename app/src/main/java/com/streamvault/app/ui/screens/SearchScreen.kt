package com.streamvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.theme.*

data class CategoryItem(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember {
        listOf(
            CategoryItem("Music", Icons.Filled.MusicNote, YouTubeRed),
            CategoryItem("Gaming", Icons.Filled.SportsEsports, Color(0xFF9333EA)),
            CategoryItem("Live", Icons.Filled.LiveTv, Color(0xFFEF4444)),
            CategoryItem("News", Icons.Filled.Newspaper, Color(0xFF3B82F6)),
            CategoryItem("Sports", Icons.Filled.Sports, Color(0xFF22C55E)),
            CategoryItem("Learning", Icons.Filled.School, Color(0xFFF59E0B)),
            CategoryItem("Podcasts", Icons.Filled.Podcasts, Color(0xFFEC4899)),
            CategoryItem("Fashion", Icons.Filled.Checkroom, Color(0xFF8B5CF6))
        )
    }

    val recentSearches = remember {
        listOf(
            "lofi hip hop radio",
            "coding tutorial python",
            "funny cat videos",
            "4k nature documentary",
            "jazz music playlist"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = DarkCard
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search videos, music, channels...",
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextMuted
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Clear",
                                tint = TextMuted
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }

        // Categories
        Text(
            text = "Browse Categories",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories) { category ->
                CategoryChip(category = category)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Searches
        if (searchQuery.isEmpty()) {
            Text(
                text = "Recent Searches",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentSearches) { search ->
                    RecentSearchItem(
                        search = search,
                        onClick = { searchQuery = search }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(category: CategoryItem) {
    Card(
        modifier = Modifier.clickable { },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = category.color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = category.color
            )
        }
    }
}

@Composable
fun RecentSearchItem(
    search: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = search,
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.NorthWest,
            contentDescription = "Insert",
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}
