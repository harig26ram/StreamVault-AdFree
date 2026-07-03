package com.streamvault.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Subscriptions : Screen("subscriptions")
    data object Library : Screen("library")
    data object Music : Screen("music")
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
}