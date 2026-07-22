package com.freedomplay.app.presentation.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.freedomplay.app.domain.model.Stream
import com.freedomplay.app.domain.model.StreamFormat

/**
 * Builds the ExoPlayer [MediaSource] for a [Stream].
 *
 * Why this exists: YouTube's high-quality tracks (1080p/1440p/4K, VP9/AV1) are *adaptive* —
 * video-only, with audio in a separate stream. Progressive (muxed) streams cap at 720p. To
 * play high quality we merge a chosen video-only track with the best audio track via
 * [MergingMediaSource], and merge in any subtitle tracks. When only muxed streams exist (or
 * audio-only mode) we fall back to a single [ProgressiveMediaSource].
 */
@OptIn(UnstableApi::class)
object PlaybackSourceFactory {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.6478.122 Mobile Safari/537.36"

    private fun dataSourceFactory(context: Context): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
        return DefaultDataSource.Factory(context, http)
    }

    /**
     * @param selectedQuality "Auto" or a resolution label such as "1080p" / "1440p60".
     * @param audioOnly when true, returns an audio-only source (best available audio track).
     * @return a prepared [MediaSource], or null if the stream has nothing playable.
     */
    fun build(
        context: Context,
        stream: Stream,
        selectedQuality: String,
        audioOnly: Boolean
    ): MediaSource? {
        val dsf = dataSourceFactory(context)

        // Live / manifest playback: when there are no individual progressive/adaptive tracks
        // (typically livestreams), play the DASH or HLS manifest directly.
        val hasIndividualStreams = stream.videoStreams.any { !it.url.isNullOrBlank() } ||
            stream.audioStreams.any { !it.url.isNullOrBlank() }
        if (!hasIndividualStreams) {
            stream.dashManifestUrl?.takeIf { it.isNotBlank() }?.let { dash ->
                return DashMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(dash))
            }
            stream.hlsManifestUrl?.takeIf { it.isNotBlank() }?.let { hls ->
                return HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(hls))
            }
        }

        val bestAudio = stream.audioStreams
            .filter { !it.url.isNullOrBlank() }
            .maxByOrNull { it.bitrate ?: 0L }

        if (audioOnly) {
            // Best available audio, in order: dedicated audio track (Opus ~160 kbps is
            // YouTube's ceiling) → DASH manifest (adaptive — ExoPlayer picks the highest
            // audio rendition) → HLS → muxed stream (its AAC track) as last resort.
            // Note: YouTube serves no lossless audio, so FLAC-level output cannot exist
            // from this source; this path guarantees the maximum that does exist.
            bestAudio?.url?.let { audioUrl ->
                return ProgressiveMediaSource.Factory(dsf)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
            }
            stream.dashManifestUrl?.takeIf { it.isNotBlank() }?.let { dash ->
                return DashMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(dash))
            }
            stream.hlsManifestUrl?.takeIf { it.isNotBlank() }?.let { hls ->
                return HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(hls))
            }
            val muxedAudio = stream.videoStreams
                .filter { !it.isVideoOnly && !it.url.isNullOrBlank() }
                .maxByOrNull { it.bitrate ?: 0L }
            return muxedAudio?.url?.let { url ->
                ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))
            }
        }

        val playableVideos = stream.videoStreams.filter { !it.url.isNullOrBlank() }
        val chosenVideo = pickVideo(playableVideos, selectedQuality)
        val subtitleSources = buildSubtitleSources(stream, dsf)

        val chosenVideoUrl = chosenVideo?.url
        val audioUrl = bestAudio?.url

        // High-quality path: adaptive video-only track merged with best audio.
        if (chosenVideo != null && chosenVideo.isVideoOnly && chosenVideoUrl != null && audioUrl != null) {
            val video = ProgressiveMediaSource.Factory(dsf)
                .createMediaSource(MediaItem.fromUri(chosenVideoUrl))
            val audio = ProgressiveMediaSource.Factory(dsf)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            val sources = mutableListOf<MediaSource>(video, audio)
            sources.addAll(subtitleSources)
            return MergingMediaSource(*sources.toTypedArray())
        }

        // Fallback: progressive muxed video (already has audio), or audio-only as last resort.
        val muxedUrl = chosenVideo?.takeIf { !it.isVideoOnly }?.url
            ?: playableVideos.filter { !it.isVideoOnly }.maxByOrNull { it.height ?: 0 }?.url
            ?: chosenVideo?.url
            ?: bestAudio?.url
            ?: return null

        val content = ProgressiveMediaSource.Factory(dsf)
            .createMediaSource(MediaItem.fromUri(muxedUrl))

        return if (subtitleSources.isEmpty()) {
            content
        } else {
            val sources = mutableListOf<MediaSource>(content)
            sources.addAll(subtitleSources)
            MergingMediaSource(*sources.toTypedArray())
        }
    }

    /** Returns the best-matching video track for the requested quality. */
    private fun pickVideo(videos: List<StreamFormat>, quality: String): StreamFormat? {
        if (videos.isEmpty()) return null

        if (quality.equals("Auto", ignoreCase = true)) {
            // Prefer the highest-resolution adaptive track (merged with audio for best quality).
            return videos.filter { it.isVideoOnly }.maxByOrNull { it.height ?: 0 }
                ?: videos.maxByOrNull { it.height ?: 0 }
        }

        val targetHeight = Regex("(\\d+)").find(quality)?.value?.toIntOrNull()
        val exact = videos.filter {
            it.quality?.startsWith(quality, ignoreCase = true) == true || it.height == targetHeight
        }
        val candidates = exact.ifEmpty {
            videos.filter { (it.height ?: 0) <= (targetHeight ?: Int.MAX_VALUE) }
        }
        // Within candidates, prefer adaptive (video-only) then highest resolution.
        return candidates
            .sortedWith(compareByDescending<StreamFormat> { it.isVideoOnly }.thenByDescending { it.height ?: 0 })
            .firstOrNull()
            ?: videos.maxByOrNull { it.height ?: 0 }
    }

    private fun buildSubtitleSources(stream: Stream, dsf: DataSource.Factory): List<MediaSource> {
        return stream.subtitles
            .filter { !it.url.isNullOrBlank() }
            .mapNotNull { sub ->
                val mime = when {
                    sub.mimeType?.contains("vtt", ignoreCase = true) == true -> MimeTypes.TEXT_VTT
                    sub.mimeType?.contains("ttml", ignoreCase = true) == true -> MimeTypes.APPLICATION_TTML
                    sub.mimeType?.contains("xml", ignoreCase = true) == true -> MimeTypes.APPLICATION_TTML
                    sub.mimeType?.contains("srv", ignoreCase = true) == true -> MimeTypes.APPLICATION_TTML
                    else -> MimeTypes.TEXT_VTT
                }
                val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(mime)
                    .setLanguage(sub.code)
                    .setSelectionFlags(0)
                    .build()
                SingleSampleMediaSource.Factory(dsf)
                    .createMediaSource(config, C.TIME_UNSET)
            }
    }
}
