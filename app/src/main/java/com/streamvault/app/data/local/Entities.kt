package com.streamvault.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.streamvault.app.domain.model.Video

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "channel_name") val channelName: String = "",
    @ColumnInfo(name = "channel_id") val channelId: String = "",
    @ColumnInfo(name = "channel_avatar") val channelAvatar: String = "",
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String = "",
    @ColumnInfo(name = "duration") val duration: String = "",
    @ColumnInfo(name = "view_count") val viewCount: String = "",
    @ColumnInfo(name = "published_time") val publishedTime: String = "",
    @ColumnInfo(name = "video_url") val videoUrl: String = "",
    @ColumnInfo(name = "watched_at") val watchedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_position_ms", defaultValue = "0") val lastPositionMs: Long = 0
) {
    fun toDomain(): Video = Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        channelAvatar = channelAvatar,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        viewCount = viewCount,
        publishedTime = publishedTime,
        videoUrl = videoUrl
    )

    companion object {
        fun fromDomain(video: Video, lastPositionMs: Long = 0): WatchHistoryEntity = WatchHistoryEntity(
            videoId = video.id,
            title = video.title,
            channelName = video.channelName,
            channelId = video.channelId,
            channelAvatar = video.channelAvatar,
            thumbnailUrl = video.thumbnailUrl,
            duration = video.duration,
            viewCount = video.viewCount,
            publishedTime = video.publishedTime,
            videoUrl = video.videoUrl ?: "",
            lastPositionMs = lastPositionMs
        )
    }
}

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "channel_name") val channelName: String = "",
    @ColumnInfo(name = "channel_avatar") val channelAvatar: String = "",
    @ColumnInfo(name = "subscribed_at") val subscribedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "channel_name") val channelName: String = "",
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String = "",
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Video = Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = "",
        channelAvatar = "",
        thumbnailUrl = thumbnailUrl,
        duration = "",
        viewCount = "",
        publishedTime = ""
    )

    companion object {
        fun fromDomain(video: Video): WatchLaterEntity = WatchLaterEntity(
            videoId = video.id,
            title = video.title,
            channelName = video.channelName,
            thumbnailUrl = video.thumbnailUrl
        )
    }
}

enum class DownloadStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "channel_name") val channelName: String = "",
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String = "",
    @ColumnInfo(name = "audio_url") val audioUrl: String = "",
    @ColumnInfo(name = "video_url") val videoUrl: String = "",
    @ColumnInfo(name = "file_path") val filePath: String = "",
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "download_status") val downloadStatus: String = DownloadStatus.PENDING.name,
    @ColumnInfo(name = "progress") val progress: Int = 0,
    @ColumnInfo(name = "downloaded_audio_bytes") val downloadedAudioBytes: Long = 0,
    @ColumnInfo(name = "downloaded_video_bytes") val downloadedVideoBytes: Long = 0,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String = ""
)