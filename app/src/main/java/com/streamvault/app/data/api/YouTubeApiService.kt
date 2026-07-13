package com.streamvault.app.data.api

import com.streamvault.app.data.model.BrowseEndpoint
import com.streamvault.app.data.model.InnerTubeResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface YouTubeApiService {

    @POST("youtubei/v1/browse")
    suspend fun browse(
        @Body request: BrowseRequest
    ): Response<InnerTubeResponse>

    @POST("youtubei/v1/next")
    suspend fun next(
        @Body request: NextRequest
    ): Response<InnerTubeResponse>

    @POST("youtubei/v1/search")
    suspend fun searchRaw(
        @Body request: SearchRequest
    ): Response<ResponseBody>

    @POST("youtubei/v1/player")
    suspend fun player(
        @Header("User-Agent") userAgent: String,
        @Body request: PlayerRequest
    ): Response<PlayerResponse>

    @POST("youtubei/v1/subscriptions")
    suspend fun subscriptions(
        @Body request: SubscriptionsRequest
    ): Response<InnerTubeResponse>

    @POST("youtubei/v1/next")
    suspend fun nextRaw(
        @Body request: NextRequest
    ): Response<ResponseBody>

    @POST("youtubei/v1/browse")
    suspend fun browseRaw(
        @Body request: BrowseRequest
    ): Response<ResponseBody>
}

data class BrowseRequest(
    val context: ClientContext,
    val browseId: String? = null,
    val params: String? = null
)

data class NextRequest(
    val context: ClientContext,
    val videoId: String? = null,
    val params: String? = null,
    val continuation: String? = null
)

data class SearchRequest(
    val context: ClientContext,
    val query: String,
    val params: String? = null
)

data class PlayerRequest(
    val context: ClientContext,
    val videoId: String,
    val params: String? = null,
    val playbackContext: PlaybackContext? = null
)

data class PlaybackContext(
    val contentPlaybackContext: ContentPlaybackContext? = null
)

data class ContentPlaybackContext(
    val html5Preference: String = "HTML5_PREF_WANTS",
    val lazyLoadEnabled: Boolean = true,
    val html5CryptoUnavailablePlaybackPolicy: String = "HTML5_CRYPTO_UNAVAILABLE_PLAYBACK_POLICY_ALLOW"
)

data class SubscriptionsRequest(
    val context: ClientContext,
    val params: String? = null
)

data class ClientContext(
    val client: ClientInfo,
    val user: UserContext? = null,
    val request: RequestContext? = null,
    val thirdParty: ThirdPartyContext? = null
)

data class ThirdPartyContext(
    val embedUrl: String? = null
)

data class ClientInfo(
    val clientName: String = "WEB",
    val clientVersion: String = "2.20260623.01.00",
    val hl: String = "en",
    val gl: String = "US",
    val androidSdkVersion: Int? = null,
    val userAgent: String? = null,
    val platform: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val visitorData: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null
)

data class UserContext(
    val lockedSafetyMode: Boolean = false
)

data class RequestContext(
    val useSsl: Boolean = true
)

data class PlayerResponse(
    val videoDetails: VideoDetails?,
    val streamingData: StreamingData?,
    val playabilityStatus: PlayabilityStatus?,
    val captions: CaptionsResponse?,
    val videoQuality: String?,
    val trackingParams: String?,
    val jsUrl: String? = null,
    val assets: PlayerAssets? = null
)

data class PlayerAssets(
    val js: String? = null
)

data class CaptionsResponse(
    val playerCaptionsTracklistRenderer: CaptionsTracklist?
)

data class CaptionsTracklist(
    val captionTracks: List<CaptionTrack>?,
    val translationLanguages: List<TranslationLanguage>?
)

data class CaptionTrack(
    val baseUrl: String?,
    val name: CaptionName?,
    val vssId: String?,
    val languageCode: String?,
    val isTranslatable: Boolean?
)

data class CaptionName(
    val simpleText: String?
)

data class TranslationLanguage(
    val languageCode: String?,
    val languageName: CaptionName?
)

data class CommentData(
    val authorText: SimpleText?,
    val contentText: SimpleText?,
    val voteCount: SimpleText?,
    val publishedTimeText: SimpleText?,
    val authorEndpoint: AuthorEndpoint?
)

data class SimpleText(
    val simpleText: String?
)

data class AuthorEndpoint(
    val browseEndpoint: BrowseEndpoint?
)

data class CommentsResponse(
    val continuationItems: List<ContinuationItem>?
)

data class ContinuationItem(
    val commentRenderer: CommentData?,
    val continuationEndpoint: ContinuationEndpoint?
)

data class ContinuationEndpoint(
    val continuationCommand: ContinuationCommand?
)

data class ContinuationCommand(
    val token: String?
)

data class VideoDetails(
    val videoId: String?,
    val title: String?,
    val channelId: String?,
    val author: String?,
    val shortDescription: String?,
    val lengthSeconds: String?,
    val viewCount: String?,
    val thumbnail: com.streamvault.app.data.model.Thumbnail?,
    val isOwnerViewing: Boolean?,
    val isPrivate: Boolean?,
    val isUnpluggedCorpus: Boolean?,
    val isLiveContent: Boolean?,
    val isUpcoming: Boolean?,
    val likeCount: String? = null
)

data class StreamingData(
    val formats: List<Format>?,
    val adaptiveFormats: List<Format>?,
    val dashManifestUrl: String?,
    val hlsManifestUrl: String?
)

data class Format(
    val itag: Int?,
    val url: String?,
    val mimeType: String?,
    val bitrate: Int?,
    val width: Int?,
    val height: Int?,
    val initRange: Range?,
    val indexRange: Range?,
    val lastModified: String?,
    val contentLength: String?,
    val quality: String?,
    val projectionType: String?,
    val container: String?,
    val highReplication: Boolean?,
    val targetDurationSec: Int?,
    val audioQuality: String?,
    val approxDurationMs: String?,
    val audioSampleRate: String?,
    val audioChannels: Int?,
    val signatureCipher: String?,
    val cipher: String?
)

data class Range(
    val start: String?,
    val end: String?
)

data class PlayabilityStatus(
    val status: String?,
    val reason: String?,
    val errorScreen: ErrorScreen?,
    val playableInnVSSupported: Boolean?,
    val miniplayerRendered: Boolean?,
    val liveStreamability: LiveStreamability?
)

data class ErrorScreen(
    val playerErrorMessageRenderer: PlayerErrorMessageRenderer?
)

data class PlayerErrorMessageRenderer(
    val reason: com.streamvault.app.data.model.Text?,
    val subreason: com.streamvault.app.data.model.Text?
)

data class LiveStreamability(
    val liveStreamabilityRenderer: LiveStreamabilityRenderer?
)

data class LiveStreamabilityRenderer(
    val offlineSlate: OfflineSlate?
)

data class OfflineSlate(
    val liveStreamOfflineSlateRenderer: LiveStreamOfflineSlateRenderer?
)

data class LiveStreamOfflineSlateRenderer(
    val title: com.streamvault.app.data.model.Text?,
    val subtitle: com.streamvault.app.data.model.Text?
)
