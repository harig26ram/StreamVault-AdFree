package com.streamvault.app.domain.repository

import com.streamvault.app.domain.model.CaptionTrack
import com.streamvault.app.domain.model.Channel
import com.streamvault.app.domain.model.Comment
import com.streamvault.app.domain.model.HomeFeed
import com.streamvault.app.domain.model.Playlist
import com.streamvault.app.domain.model.SearchResult
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.model.VideoFormat
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    suspend fun getHomeFeed(continuationToken: String?): Result<HomeFeed>
    suspend fun search(query: String, continuationToken: String?): Result<SearchResult>
    suspend fun getVideoInfo(videoId: String): Result<Video>
    suspend fun getChannelInfo(channelId: String): Result<Channel>
    suspend fun getPlaylist(playlistId: String): Result<Playlist>
    suspend fun getTrending(): Result<HomeFeed>
    suspend fun getSubscriptions(): Result<HomeFeed>
    suspend fun getVideoStreamUrl(videoId: String): Result<String>
    suspend fun getVideoFormats(videoId: String): Result<List<VideoFormat>>
    suspend fun getCaptionTracks(videoId: String): Result<List<CaptionTrack>>
    suspend fun getComments(videoId: String): Result<List<Comment>>
    suspend fun getRelatedVideos(videoId: String): Result<List<com.streamvault.app.domain.model.Video>>
    fun getWatchHistory(): Flow<List<Video>>
    suspend fun addToWatchHistory(video: Video)
    suspend fun clearWatchHistory()
    fun getSubscriptionsList(): Flow<List<Channel>>
    suspend fun subscribeToChannel(channelId: String)
    suspend fun unsubscribeFromChannel(channelId: String)
}