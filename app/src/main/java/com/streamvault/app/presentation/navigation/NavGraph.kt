package com.streamvault.app.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamvault.app.presentation.ui.components.BottomNavBar
import com.streamvault.app.presentation.ui.components.MiniPlayer
import com.streamvault.app.presentation.ui.components.MiniPlayerManager
import com.streamvault.app.presentation.ui.screen.*
import com.streamvault.app.presentation.viewmodel.EqualizerViewModel
import com.streamvault.app.presentation.viewmodel.PlayerViewModel
import com.streamvault.player.core.PlayerState

@Composable
fun MainNavGraph(
    startDestination: String = Screen.Splash.route,
    deepLinkUri: Uri? = null,
    miniPlayerManager: MiniPlayerManager = hiltViewModel<com.streamvault.app.presentation.viewmodel.SplashViewModel>().miniPlayerManager
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val miniPlayerState by miniPlayerManager.state.collectAsState()

    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { uri ->
            val host = uri.host ?: return@let
            if (host != "www.youtube.com") return@let
            val path = uri.path ?: ""
            val videoId = uri.getQueryParameter("v")
            when {
                videoId != null -> {
                    navController.navigate(Screen.Player.createRoute(videoId)) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
                path.startsWith("/channel/") -> {
                    val channelId = path.removePrefix("/channel/")
                    if (channelId.isNotEmpty()) {
                        navController.navigate(Screen.Channel.createRoute(channelId)) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                }
                path.startsWith("/@") -> {
                    val channelHandle = path.removePrefix("/@")
                    if (channelHandle.isNotEmpty()) {
                        navController.navigate(Screen.Channel.createRoute(channelHandle)) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                }
            }
        }
    }

    val bottomNavScreens = listOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Subscriptions.route,
        Screen.Library.route,
        Screen.Trending.route
    )

    val showBottomBar = currentRoute in bottomNavScreens

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Splash.route) {
                    SplashScreen(
                        onTimeout = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        },
                        onChannelClick = { channelId ->
                            navController.navigate(Screen.Channel.createRoute(channelId))
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.Playlist.createRoute(playlistId))
                        },
                        onSettingsClick = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        },
                        onChannelClick = { channelId ->
                            navController.navigate(Screen.Channel.createRoute(channelId))
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.Playlist.createRoute(playlistId))
                        }
                    )
                }

                composable(Screen.Subscriptions.route) {
                    SubscriptionsScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        },
                        onChannelClick = { channelId ->
                            navController.navigate(Screen.Channel.createRoute(channelId))
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.Playlist.createRoute(playlistId))
                        }
                    )
                }

                composable(Screen.Trending.route) {
                    TrendingScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToLogin = {
                            navController.navigate(Screen.Login.route)
                        },
                        onNavigateToEqualizer = {
                            navController.navigate(Screen.Equalizer.route)
                        }
                    )
                }

                composable(Screen.Login.route) {
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoginSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.Player.route,
                    arguments = listOf(
                        navArgument("videoId") { type = NavType.StringType }
                    )
                ) {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onChannelClick = { channelId ->
                            navController.navigate(Screen.Channel.createRoute(channelId))
                        },
                        onEqualizerClick = {
                            navController.navigate(Screen.Equalizer.route)
                        }
                    )
                }

                composable(
                    route = Screen.Channel.route,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType }
                    )
                ) {
                    ChannelScreen(
                        onBack = { navController.popBackStack() },
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        }
                    )
                }

                composable(
                    route = Screen.Playlist.route,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType }
                    )
                ) {
                    PlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onVideoClick = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId))
                        }
                    )
                }

                composable(Screen.Equalizer.route) {
                    EqualizerScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Mini Player Overlay
            if (miniPlayerState.isActive && miniPlayerState.video != null && currentRoute != Screen.Player.route) {
                MiniPlayer(
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(10f),
                    title = miniPlayerState.video?.title ?: "",
                    channelName = miniPlayerState.video?.channelName ?: "",
                    thumbnailUrl = miniPlayerState.thumbnailUrl ?: "",
                    isPlaying = miniPlayerState.isPlaying,
                    progress = if (miniPlayerState.duration > 0) miniPlayerState.position.toFloat() / miniPlayerState.duration.toFloat() else 0f,
                    onClick = {
                        miniPlayerState.video?.let { video ->
                            navController.navigate(Screen.Player.createRoute(video.id))
                        }
                    },
                    onPlayPause = {
                        miniPlayerManager.getEngine()?.let { engine ->
                            if (miniPlayerState.isPlaying) engine.pause() else engine.play()
                            miniPlayerManager.updatePlaybackState(
                                isPlaying = !miniPlayerState.isPlaying,
                                position = miniPlayerState.position,
                                duration = miniPlayerState.duration,
                                bufferedPercent = miniPlayerState.bufferedPercent
                            )
                        }
                    },
                    onClose = { miniPlayerManager.deactivate() },
                    onQueueClick = { /* TODO: Show queue */ }
                )
            }
        }
    }
}
