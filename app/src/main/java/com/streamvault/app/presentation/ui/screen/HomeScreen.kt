package com.streamvault.app.presentation.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.streamvault.app.presentation.ui.components.MTricolorDivider
import com.streamvault.app.presentation.ui.components.BrandRingAvatar
import com.streamvault.app.presentation.ui.components.TopBarGradientOverlay
import com.streamvault.app.presentation.ui.components.MagazineFeed
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
    val gridState = rememberLazyGridState()
    val context = LocalContext.current

    val isCastConnected by castSessionManager.isConnected.collectAsState()
    val castDeviceName by castSessionManager.currentDeviceName.collectAsState()
    val isCastAvailable by castSessionManager.isAvailable.collectAsState()
    var showCastDialog by remember { mutableStateOf(false) }

    // Load more when scrolling to bottom
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !uiState.isLoading) {
                viewModel.loadMore()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        state = rememberPullToRefreshState()
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top app bar with soft brand gradient overlay
        Box {
            TopBarGradientOverlay(height = 64.dp)
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
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
                            BrandRingAvatar(
                                model = userProfile?.photoUrl,
                                contentDescription = "Profile",
                                size = 30.dp,
                                ringWidth = 2.dp,
                                onClick = onSettingsClick
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
                    containerColor = Color.Transparent
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }

        MTricolorDivider()

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
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.feedIsLocalFallback) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSettingsClick() },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.feed_fallback_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(R.string.reconnect),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    MagazineFeed(
                        feedItems = uiState.feedItems,
                        onVideoClick = onVideoClick,
                        onChannelClick = onChannelClick,
                        onPlaylistClick = onPlaylistClick,
                        modifier = Modifier.fillMaxSize(),
                        gridState = gridState
                    )
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
