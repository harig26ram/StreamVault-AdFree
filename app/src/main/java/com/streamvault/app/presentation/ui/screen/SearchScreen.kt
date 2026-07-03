package com.streamvault.app.presentation.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamvault.app.presentation.ui.components.LoadingIndicator
import com.streamvault.app.presentation.ui.components.SearchBar
import com.streamvault.app.presentation.ui.components.VideoCard
import com.streamvault.app.presentation.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHistory by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar
        SearchBar(
            query = uiState.query,
            onQueryChange = { viewModel.onQueryChange(it) },
            onSearch = { viewModel.search() },
            onClear = { viewModel.clearSearch() },
            modifier = Modifier.padding(16.dp)
        )

        // Content
        when {
            uiState.query.isBlank() && uiState.searchHistory.isNotEmpty() -> {
                // Search history
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    uiState.searchHistory.reversed().take(10).forEach { query ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onQueryChange(query)
                                    viewModel.search(query)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = query,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { viewModel.removeFromHistory(query) }) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            uiState.isLoading && uiState.results.isEmpty() -> {
                LoadingIndicator()
            }
            uiState.error != null && uiState.results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.results,
                        key = { it.id }
                    ) { feedItem ->
                        when (feedItem) {
                            is com.streamvault.app.domain.model.FeedItem.Video -> {
                                VideoCard(
                                    video = feedItem.video,
                                    onClick = { onVideoClick(feedItem.video.id) }
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}