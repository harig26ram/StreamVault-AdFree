package com.freedomplay.app.data.api.invidious

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class InvidiousVideoResponse(
    val title: String? = null,
    val author: String? = null,
    val authorUrl: String? = null,
    val authorId: String? = null,
    val thumbnailUrl: String? = null,
    val lengthSeconds: Long? = null,
    val viewCount: Long? = null,
    val published: Long? = null,
    val publishedText: String? = null,
    val description: String? = null,
    val descriptionHtml: String? = null,
    val formatStreams: List<InvidiousFormatStream>? = null,
    val adaptiveFormats: List<InvidiousAdaptiveFormat>? = null,
    val captions: List<InvidiousCaption>? = null,
    val liveNow: Boolean? = null
)

data class InvidiousFormatStream(
    val url: String? = null,
    val itag: String? = null,
    val type: String? = null,
    val quality: String? = null,
    val bitrate: String? = null,
    val fps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val contentLength: String? = null
)

data class InvidiousAdaptiveFormat(
    val url: String? = null,
    val itag: String? = null,
    val type: String? = null,
    val quality: String? = null,
    val bitrate: String? = null,
    val fps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val initStart: Long? = null,
    val initEnd: Long? = null,
    val indexStart: Long? = null,
    val indexEnd: Long? = null,
    val contentLength: String? = null
)

data class InvidiousCaption(
    val url: String? = null,
    val name: String? = null,
    val label: String? = null,
    val languageCode: String? = null
)

data class InvidiousSearchResponse(
    val items: List<InvidiousSearchItem>? = null,
    val continuation: String? = null
)

data class InvidiousSearchItem(
    val title: String? = null,
    val videoId: String? = null,
    val author: String? = null,
    val authorUrl: String? = null,
    val authorId: String? = null,
    val lengthSeconds: Long? = null,
    val viewCount: Long? = null,
    val published: Long? = null,
    val publishedText: String? = null,
    val description: String? = null,
    val type: String? = null
)

data class InvidiousChannelResponse(
    val author: String? = null,
    val authorUrl: String? = null,
    val authorId: String? = null,
    val authorThumbnails: List<InvidiousThumbnail>? = null,
    val subscriberCount: Long? = null,
    val description: String? = null,
    val totalVideos: Long? = null,
    val videos: List<InvidiousSearchItem>? = null,
    val continuation: String? = null
)

data class InvidiousThumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class InvidiousTrendingResponse(
    val items: List<InvidiousSearchItem>? = null
)

interface InvidiousApiService {

    @GET("api/v1/trending")
    suspend fun getTrending(
        @Query("region") region: String = "US"
    ): List<InvidiousSearchItem>

    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "relevance",
        @Query("type") type: String = "video"
    ): List<InvidiousSearchItem>

    @GET("api/v1/videos/{id}")
    suspend fun getVideo(
        @Path("id") videoId: String
    ): InvidiousVideoResponse

    @GET("api/v1/channels/{id}")
    suspend fun getChannel(
        @Path("id") channelId: String
    ): InvidiousChannelResponse

    @GET("api/v1/channels/{id}/videos")
    suspend fun getChannelVideos(
        @Path("id") channelId: String,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "newest"
    ): InvidiousChannelResponse

    @GET("api/v1/search/suggestions")
    suspend fun getSuggestions(
        @Query("q") query: String
    ): List<String>
}
