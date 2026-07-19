package com.freedomplay.app.data.extractor

import com.freedomplay.app.domain.model.Stream
import com.freedomplay.app.domain.model.StreamFormat
import com.freedomplay.app.domain.model.StreamItem
import com.freedomplay.app.domain.model.Subtitle
import com.freedomplay.app.util.CrashLogger
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side YouTube extraction via NewPipeExtractor.
 *
 * This is the primary source for streams/search/trending/suggestions. Unlike the raw
 * InnerTube path in [com.freedomplay.app.data.repository.StreamRepository], NewPipeExtractor
 * runs the YouTube JS player through Rhino to decipher `sig`/`nsig`, so the returned
 * stream URLs are actually playable — including high-quality adaptive (video-only + audio)
 * tracks that go well beyond the 720p progressive ceiling.
 *
 * All methods are blocking and MUST be called from a background dispatcher (the repository
 * already wraps them in `withContext(Dispatchers.IO)`). Every call is defensively guarded:
 * on any extraction failure it returns null / empty so the repository can fall back to
 * Piped/Invidious.
 */
@Singleton
class NewPipeStreamSource @Inject constructor() {

    private val youtube get() = ServiceList.YouTube

    fun getStream(videoId: String): Stream? {
        return try {
            val extractor = youtube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val muxed = safe { extractor.videoStreams }.orEmpty()
            val videoOnly = safe { extractor.videoOnlyStreams }.orEmpty()
            val audios = safe { extractor.audioStreams }.orEmpty()

            val videoFormats = (muxed.mapNotNull { it.toStreamFormat(videoOnly = false) } +
                videoOnly.mapNotNull { it.toStreamFormat(videoOnly = true) })
                .sortedByDescending { it.height ?: 0 }

            val audioFormats = audios.mapNotNull { it.toStreamFormat() }
                .sortedByDescending { it.bitrate ?: 0L }

            if (videoFormats.isEmpty() && audioFormats.isEmpty()) {
                CrashLogger.d("NewPipe returned no playable streams for $videoId")
                return null
            }

            val streamType = safe { extractor.streamType }
            val isLive = streamType == StreamType.LIVE_STREAM ||
                streamType == StreamType.AUDIO_LIVE_STREAM

            CrashLogger.d("NewPipe getStream $videoId: ${videoFormats.size} video, ${audioFormats.size} audio")

            Stream(
                title = safe { extractor.name }?.takeIf { it.isNotBlank() } ?: "Unknown",
                uploader = safe { extractor.uploaderName }?.takeIf { it.isNotBlank() } ?: "Unknown",
                uploaderUrl = safe { extractor.uploaderUrl },
                thumbnailUrl = bestImageUrl(safe { extractor.thumbnails })
                    ?: "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                duration = safe { extractor.length }?.takeIf { it > 0 },
                views = safe { extractor.viewCount }?.takeIf { it >= 0 },
                uploaded = null,
                uploadDate = safe { extractor.textualUploadDate },
                description = safe { extractor.description?.content },
                videoStreams = videoFormats,
                audioStreams = audioFormats,
                livestream = isLive,
                subtitles = safe { extractor.subtitlesDefault }.orEmpty().mapNotNull { it.toSubtitle() },
                dashManifestUrl = safe { extractor.dashMpdUrl }?.takeIf { it.isNotBlank() },
                hlsManifestUrl = safe { extractor.hlsUrl }?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            CrashLogger.d("NewPipe getStream failed for $videoId: ${e.message}")
            null
        }
    }

    fun search(query: String): List<StreamItem> {
        return try {
            val queryHandler = youtube.searchQHFactory.fromQuery(query, listOf("videos"), "")
            val info = SearchInfo.getInfo(youtube, queryHandler)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toStreamItem() }
        } catch (e: Exception) {
            CrashLogger.d("NewPipe search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    fun getTrending(): List<StreamItem> {
        return try {
            val extractor = youtube.kioskList.defaultKioskExtractor
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toStreamItem() }
        } catch (e: Exception) {
            CrashLogger.d("NewPipe trending failed: ${e.message}")
            emptyList()
        }
    }

    fun getSuggestions(query: String): List<String> {
        return try {
            youtube.suggestionExtractor?.suggestionList(query).orEmpty()
        } catch (e: Exception) {
            CrashLogger.d("NewPipe suggestions failed for '$query': ${e.message}")
            emptyList()
        }
    }

    // --- Mapping helpers -------------------------------------------------------------------

    private fun VideoStream.toStreamFormat(videoOnly: Boolean): StreamFormat? {
        if (!isUrl) return null
        val streamUrl = content?.takeIf { it.isNotBlank() } ?: return null
        val h = parseHeight(resolution)
        return StreamFormat(
            url = streamUrl,
            quality = resolution?.takeIf { it.isNotBlank() } ?: h?.let { "${it}p" },
            mimeType = safe { format?.mimeType },
            codec = safe { format?.getName() },
            bitrate = null,
            width = null,
            height = h,
            fps = fps.takeIf { it > 0 },
            isVideoOnly = videoOnly
        )
    }

    private fun AudioStream.toStreamFormat(): StreamFormat? {
        if (!isUrl) return null
        val streamUrl = content?.takeIf { it.isNotBlank() } ?: return null
        val br = averageBitrate.takeIf { it > 0 }?.toLong()
        return StreamFormat(
            url = streamUrl,
            quality = br?.let { "${it / 1000} kbps" },
            mimeType = safe { format?.mimeType },
            codec = safe { format?.getName() },
            bitrate = br,
            width = null,
            height = null,
            fps = null,
            isVideoOnly = false
        )
    }

    private fun SubtitlesStream.toSubtitle(): Subtitle? {
        val subUrl = (if (isUrl) content else null)?.takeIf { it.isNotBlank() } ?: return null
        return Subtitle(
            url = subUrl,
            mimeType = safe { format?.mimeType },
            name = safe { displayLanguageName },
            code = safe { languageTag },
            autoGenerated = safe { isAutoGenerated }
        )
    }

    private fun StreamInfoItem.toStreamItem(): StreamItem {
        val itemUrl = url ?: ""
        val videoId = extractVideoId(itemUrl)
        return StreamItem(
            url = itemUrl,
            videoId = videoId,
            title = name?.takeIf { it.isNotBlank() } ?: "Unknown",
            thumbnail = bestImageUrl(safe { thumbnails })
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            uploaderName = uploaderName?.takeIf { it.isNotBlank() } ?: "Unknown",
            uploaderUrl = uploaderUrl,
            uploaderAvatar = bestImageUrl(safe { uploaderAvatars }),
            views = safe { viewCount }?.takeIf { it >= 0 } ?: 0L,
            duration = safe { duration }?.takeIf { it > 0 },
            uploadedDate = safe { textualUploadDate },
            uploaded = null
        )
    }

    private fun bestImageUrl(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        val best = images.maxByOrNull { it.height.takeIf { h -> h > 0 } ?: 0 } ?: images.last()
        return best.url?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun parseHeight(resolution: String?): Int? {
        if (resolution.isNullOrBlank()) return null
        return HEIGHT_REGEX.find(resolution)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> url.trimEnd('/').substringAfterLast("/")
        }
    }

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    }

    private companion object {
        val HEIGHT_REGEX = Regex("(\\d+)p")
    }
}
