package com.freedomplay.app.util

object UrlUtils {

    fun extractVideoId(url: String): String? {
        if (url.isBlank()) return null

        val cleanUrl = url.trim()

        if (cleanUrl.length == 11 && cleanUrl.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            return cleanUrl
        }

        val videoId = when {
            "youtu.be/" in cleanUrl -> {
                val start = cleanUrl.indexOf("youtu.be/") + "youtu.be/".length
                extractIdFromSegment(cleanUrl, start)
            }
            ("youtube.com" in cleanUrl || "m.youtube.com" in cleanUrl || "music.youtube.com" in cleanUrl) && ("v=" in cleanUrl) -> {
                val start = cleanUrl.indexOf("v=") + 2
                val end = cleanUrl.indexOfAny(charArrayOf('&', '#', '?', '%'), startIndex = start)
                    .let { if (it == -1) cleanUrl.length else it }
                cleanUrl.substring(start, end.coerceAtMost(cleanUrl.length))
            }
            "youtube.com" in cleanUrl && "/shorts/" in cleanUrl -> {
                val idx = cleanUrl.indexOf("/shorts/") + "/shorts/".length
                extractIdFromSegment(cleanUrl, idx)
            }
            "youtube.com" in cleanUrl && "/embed/" in cleanUrl -> {
                val idx = cleanUrl.indexOf("/embed/") + "/embed/".length
                extractIdFromSegment(cleanUrl, idx)
            }
            else -> null
        }

        return videoId?.takeIf { it.length == 11 && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
    }

    private fun extractIdFromSegment(url: String, startIndex: Int): String {
        val end = url.indexOfAny(charArrayOf('&', '#', '?', '/', '%'), startIndex = startIndex)
            .let { if (it == -1) url.length else it }
        return url.substring(startIndex, end.coerceAtMost(url.length))
    }

    fun getVideoUrl(id: String): String = "https://www.youtube.com/watch?v=$id"

    fun getChannelUrl(id: String): String = "https://www.youtube.com/channel/$id"

    fun getThumbnailUrl(videoId: String, quality: String = "medium"): String {
        val resolution = when (quality) {
            "default" -> "default"
            "medium" -> "mqdefault"
            "high" -> "hqdefault"
            "standard" -> "sddefault"
            "maxres" -> "maxresdefault"
            else -> "mqdefault"
        }
        return "https://img.youtube.com/vi/$videoId/$resolution.jpg"
    }
}
