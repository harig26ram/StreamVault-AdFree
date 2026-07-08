package com.streamvault.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Subscriptions : Screen("subscriptions")
    data object Library : Screen("library")
    data object Trending : Screen("trending")
    data object Settings : Screen("settings")
    data object Login : Screen("login")
    data object Player : Screen("player/{videoId}") {
        fun createRoute(videoId: String) = "player/$videoId"
    }
    data object Channel : Screen("channel/{channelId}") {
        fun createRoute(channelId: String) = "channel/$channelId"
    }
    data object Playlist : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object Equalizer : Screen("equalizer")
}