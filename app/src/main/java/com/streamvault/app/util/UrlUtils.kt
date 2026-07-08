package com.streamvault.app.util

import java.net.URL

object UrlUtils {

    private val VIDEO_ID_PATTERNS = listOf(
        Regex("""(?:youtube\.com/watch\?.*v=|youtu\.be/|youtube\.com/embed/|youtube\.com/v/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""),
        Regex("""^([a-zA-Z0-9_-]{11})$""")
    )

    private val CHANNEL_ID_PATTERNS = listOf(
        Regex("""(?:youtube\.com/channel/)([a-zA-Z0-9_-]+)"""),
        Regex("""(?:youtube\.com/c/|youtube\.com/user/)([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/@([a-zA-Z0-9_.-]+)""")
    )

    fun extractVideoId(url: String): String? {
        for (pattern in VIDEO_ID_PATTERNS) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun extractChannelId(url: String): String? {
        for (pattern in CHANNEL_ID_PATTERNS) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = URL(url)
            parsed.protocol == "http" || parsed.protocol == "https"
        } catch (_: Exception) {
            false
        }
    }

    fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("www.")) return "https://$trimmed"
        return "https://$trimmed"
    }

    fun getYouTubeThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    fun buildYouTubeWatchUrl(videoId: String): String {
        return "https://www.youtube.com/watch?v=$videoId"
    }

    fun buildYouTubeChannelUrl(channelId: String): String {
        return "https://www.youtube.com/channel/$channelId"
    }
}
