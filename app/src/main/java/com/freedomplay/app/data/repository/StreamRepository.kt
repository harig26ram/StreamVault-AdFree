package com.freedomplay.app.data.repository

import com.freedomplay.app.data.api.invidious.InvidiousApiService
import com.freedomplay.app.data.api.invidious.InvidiousSearchItem
import com.freedomplay.app.data.api.invidious.InvidiousVideoResponse
import com.freedomplay.app.data.api.piped.PipedApiService
import com.freedomplay.app.data.api.piped.PipedSearchItem
import com.freedomplay.app.data.api.piped.PipedTrendingItem
import com.freedomplay.app.data.api.piped.PipedVideoResponse
import com.freedomplay.app.domain.model.Stream
import com.freedomplay.app.domain.model.StreamFormat
import com.freedomplay.app.domain.model.StreamItem
import com.freedomplay.app.domain.model.Subtitle
import com.freedomplay.app.util.CrashLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRepository @Inject constructor(
    private val pipedApi: PipedApiService,
    private val invidiousApi: InvidiousApiService
) {
    suspend fun getTrending(): Result<List<StreamItem>> {
        return try {
            val response = pipedApi.getTrending()
            val items = response.items?.map { it.toStreamItem() } ?: emptyList()
            CrashLogger.d("Piped trending: ${items.size} items")
            Result.success(items)
        } catch (e: Exception) {
            CrashLogger.e("Piped trending failed, trying Invidious", e)
            try {
                val response = invidiousApi.getTrending()
                val items = response.map { it.toStreamItem() }
                CrashLogger.d("Invidious trending fallback: ${items.size} items")
                Result.success(items)
            } catch (e2: Exception) {
                CrashLogger.e("Invidious trending also failed", e2)
                Result.failure(e)
            }
        }
    }

    suspend fun search(query: String): Result<List<StreamItem>> {
        return try {
            val response = pipedApi.search(query)
            val items = response.items?.map { it.toStreamItem() } ?: emptyList()
            CrashLogger.d("Piped search '$query': ${items.size} results")
            Result.success(items)
        } catch (e: Exception) {
            CrashLogger.e("Piped search failed, trying Invidious", e)
            try {
                val response = invidiousApi.search(query)
                val items = response.map { it.toStreamItem() }
                CrashLogger.d("Invidious search fallback: ${items.size} results")
                Result.success(items)
            } catch (e2: Exception) {
                CrashLogger.e("Invidious search also failed", e2)
                Result.failure(e)
            }
        }
    }

    suspend fun getStreams(videoId: String): Result<Stream> {
        return try {
            val response = pipedApi.getVideoStreams(videoId)
            CrashLogger.d("Piped streams for $videoId: ${response.videoStreams?.size ?: 0} video, ${response.audioStreams?.size ?: 0} audio")
            Result.success(response.toStream())
        } catch (e: Exception) {
            CrashLogger.e("Piped streams failed, trying Invidious", e)
            try {
                val response = invidiousApi.getVideo(videoId)
                Result.success(response.toStream())
            } catch (e2: Exception) {
                CrashLogger.e("Invidious streams also failed", e2)
                Result.failure(e)
            }
        }
    }

    suspend fun getSuggestions(query: String): Result<List<String>> {
        return try {
            val suggestions = pipedApi.getSuggestions(query)
            Result.success(suggestions)
        } catch (e: Exception) {
            CrashLogger.w("Suggestions failed: ${e.message}")
            Result.success(emptyList())
        }
    }
}

private fun extractVideoId(url: String): String {
    return when {
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
        url.contains("channel/") -> url.substringAfter("channel/")
        else -> url.trimStart('/')
    }
}

private fun PipedSearchItem.toStreamItem() = StreamItem(
    url = url ?: "",
    videoId = extractVideoId(url ?: ""),
    title = title ?: "Unknown",
    thumbnail = thumbnail ?: "",
    uploaderName = uploaderName ?: "Unknown",
    uploaderUrl = uploaderUrl,
    uploaderAvatar = uploaderAvatar,
    views = views ?: 0,
    duration = duration,
    uploadedDate = uploadedDate,
    uploaded = uploaded
)

private fun PipedTrendingItem.toStreamItem() = StreamItem(
    url = url ?: "",
    videoId = extractVideoId(url ?: ""),
    title = title ?: "Unknown",
    thumbnail = thumbnail ?: "",
    uploaderName = uploaderName ?: "Unknown",
    uploaderUrl = uploaderUrl,
    uploaderAvatar = uploaderAvatar,
    views = views ?: 0,
    duration = duration,
    uploadedDate = uploadedDate,
    uploaded = uploaded
)

private fun InvidiousSearchItem.toStreamItem() = StreamItem(
    url = "/watch?v=${videoId ?: ""}",
    videoId = videoId ?: "",
    title = title ?: "Unknown",
    thumbnail = "https://i.ytimg.com/vi/${videoId}/hqdefault.jpg",
    uploaderName = author ?: "Unknown",
    uploaderUrl = authorUrl,
    uploaderAvatar = null,
    views = viewCount ?: 0,
    duration = lengthSeconds,
    uploadedDate = publishedText,
    uploaded = published
)

private fun PipedVideoResponse.toStream() = Stream(
    title = title ?: "Unknown",
    uploader = uploader ?: "Unknown",
    uploaderUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    duration = duration,
    views = views,
    uploaded = uploaded,
    uploadDate = uploadDate,
    description = description,
    videoStreams = videoStreams?.map { it.toStreamFormat() } ?: emptyList(),
    audioStreams = audioStreams?.map { it.toStreamFormat() } ?: emptyList(),
    livestream = livestream,
    subtitles = subtitle?.map { it.toSubtitle() } ?: emptyList()
)

private fun InvidiousVideoResponse.toStream() = Stream(
    title = title ?: "Unknown",
    uploader = author ?: "Unknown",
    uploaderUrl = authorUrl,
    thumbnailUrl = thumbnailUrl,
    duration = lengthSeconds,
    views = viewCount,
    uploaded = published,
    uploadDate = publishedText,
    description = descriptionHtml ?: description,
    videoStreams = (formatStreams?.map { it.toStreamFormat() } ?: emptyList()) +
            (adaptiveFormats?.map { it.toStreamFormat() } ?: emptyList()),
    audioStreams = (adaptiveFormats?.filter { it.type?.startsWith("audio") == true }?.map { it.toStreamFormat() } ?: emptyList()),
    livestream = liveNow,
    subtitles = captions?.map { Subtitle(url = it.url, mimeType = null, name = it.label, code = it.languageCode, autoGenerated = null) } ?: emptyList()
)

private fun com.freedomplay.app.data.api.piped.PipedStream.toStreamFormat() = StreamFormat(
    url = url,
    quality = quality,
    mimeType = mimeType,
    codec = codec,
    bitrate = bitrate,
    width = width,
    height = height,
    fps = fps
)

private fun com.freedomplay.app.data.api.invidious.InvidiousFormatStream.toStreamFormat() = StreamFormat(
    url = url,
    quality = quality,
    mimeType = type,
    codec = null,
    bitrate = null,
    width = width,
    height = height,
    fps = fps
)

private fun com.freedomplay.app.data.api.invidious.InvidiousAdaptiveFormat.toStreamFormat() = StreamFormat(
    url = url,
    quality = quality,
    mimeType = type,
    codec = null,
    bitrate = null,
    width = width,
    height = height,
    fps = fps
)

private fun com.freedomplay.app.data.api.piped.PipedSubtitle.toSubtitle() = Subtitle(
    url = url,
    mimeType = mimeType,
    name = name,
    code = code,
    autoGenerated = autoGenerated
)
