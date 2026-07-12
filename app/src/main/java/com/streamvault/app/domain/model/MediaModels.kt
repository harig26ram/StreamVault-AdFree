package com.streamvault.app.domain.model

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val channelAvatar: String,
    val thumbnailUrl: String,
    val duration: String,
    val viewCount: String,
    val publishedTime: String,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val videoUrl: String? = null,
    val description: String = "",
    val likeCount: String = ""
) {
    val watchUrl = "https://www.youtube.com/watch?v=$id"
}

data class Channel(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCount: String,
    val isVerified: Boolean = false,
    val videos: List<Video> = emptyList()
)

data class Playlist(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val channelName: String = "",
    val videos: List<Video> = emptyList()
)

data class VideoFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val width: Int?,
    val height: Int?,
    val qualityLabel: String,
    val isAdaptive: Boolean
) {
    val isVideo get() = mimeType.startsWith("video/")
    val isAudio get() = mimeType.startsWith("audio/")
    val displayLabel: String get() {
        val type = if (isVideo) "video" else "audio"
        val res = if (height != null) "${height}p" else qualityLabel
        val codec = mimeType.substringAfter("codecs=\"", "").substringBefore("\"").substringBefore(",")
        return "$res ($type, $codec)"
    }
}

data class CaptionTrack(
    val baseUrl: String,
    val name: String,
    val languageCode: String,
    val isTranslatable: Boolean
)

data class Comment(
    val authorName: String,
    val content: String,
    val voteCount: String,
    val publishedTime: String
)

data class Chapter(
    val title: String,
    val startTimeMs: Long
) {
    val formattedTime: String get() {
        val totalSec = startTimeMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }
}

data class SearchResult(
    val items: List<FeedItem>,
    val continuationToken: String?
)

data class HomeFeed(
    val items: List<FeedItem>,
    val continuationToken: String?
)

sealed class FeedItem {
    val id: String get() = when (this) {
        is Video -> video.id
        is Playlist -> playlist.id
        is Channel -> channel.id
        is CarouselItem -> items.firstOrNull()?.id ?: ""
    }

    data class Video(val video: com.streamvault.app.domain.model.Video) : FeedItem()
    data class Playlist(val playlist: com.streamvault.app.domain.model.Playlist) : FeedItem()
    data class Channel(val channel: com.streamvault.app.domain.model.Channel) : FeedItem()
    data class CarouselItem(val items: List<com.streamvault.app.domain.model.Video>) : FeedItem()
}