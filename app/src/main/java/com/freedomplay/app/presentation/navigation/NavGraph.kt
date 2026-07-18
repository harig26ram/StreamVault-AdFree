package com.freedomplay.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freedomplay.app.presentation.ui.screens.home.HomeScreen
import com.freedomplay.app.presentation.ui.screens.library.LibraryScreen
import com.freedomplay.app.presentation.ui.screens.player.PlayerScreen
import com.freedomplay.app.presentation.ui.screens.search.SearchScreen
import com.freedomplay.app.presentation.ui.screens.settings.SettingsScreen

@Composable
fun FreedomPlayNavGraph(
    navController: NavHostController = rememberNavController(),
    onVideoStarted: (videoId: String, title: String, thumbnail: String) -> Unit = { _, _, _ -> }
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onVideoClick = { video ->
                    onVideoStarted(video.videoId, video.title, video.thumbnail)
                    navController.navigate(Screen.Player.createRoute(video.videoId))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onVideoClick = { video ->
                    onVideoStarted(video.videoId, video.title, video.thumbnail)
                    navController.navigate(Screen.Player.createRoute(video.videoId))
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onVideoClick = { videoId ->
                    navController.navigate(Screen.Player.createRoute(videoId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            PlayerScreen(
                videoId = videoId,
                onBack = { navController.popBackStack() },
                onVideoLoaded = { title, thumbnail ->
                    onVideoStarted(videoId, title, thumbnail)
                }
            )
        }
    }
}
