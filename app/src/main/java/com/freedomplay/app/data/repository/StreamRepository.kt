package com.freedomplay.app.data.repository

import android.webkit.CookieManager
import com.freedomplay.app.data.api.invidious.InvidiousSearchItem
import com.freedomplay.app.data.api.invidious.InvidiousVideoResponse
import com.freedomplay.app.data.api.piped.PipedApiService
import com.freedomplay.app.data.api.piped.PipedSearchItem
import com.freedomplay.app.data.api.piped.PipedTrendingItem
import com.freedomplay.app.data.api.piped.PipedTrendingResponse
import com.freedomplay.app.data.api.piped.PipedVideoResponse
import com.freedomplay.app.data.extractor.NewPipeStreamSource
import com.freedomplay.app.data.manager.InstanceConfig
import com.freedomplay.app.data.manager.InstanceManager
import com.freedomplay.app.data.manager.InstanceType
import com.freedomplay.app.di.NetworkModule
import com.freedomplay.app.domain.model.MusicSection
import com.freedomplay.app.domain.model.Stream
import com.freedomplay.app.domain.model.StreamFormat
import com.freedomplay.app.domain.model.StreamItem
import com.freedomplay.app.domain.model.Subtitle
import com.freedomplay.app.util.CrashLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRepository @Inject constructor(
    private val pipedApi: PipedApiService,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val instanceManager: InstanceManager,
    private val newPipeSource: NewPipeStreamSource,
    private val preferencesManager: com.freedomplay.app.data.local.preferences.PreferencesManager
) {
    private val shortTimeoutClient = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    private fun getOrderedPipedInstances(): List<InstanceConfig> {
        return instanceManager.getOrderedInstances(
            NetworkModule.PIPED_FALLBACK_URLS.mapIndexed { index, url ->
                InstanceConfig(url = url, type = InstanceType.PIPED, priority = index)
            }
        )
    }

    private fun getOrderedInvidiousInstances(): List<InstanceConfig> {
        return instanceManager.getOrderedInstances(
            NetworkModule.INVIDIOUS_FALLBACK_URLS.mapIndexed { index, url ->
                InstanceConfig(url = url, type = InstanceType.INVIDIOUS, priority = index)
            }
        )
    }

    private fun buildRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")
        .header("Accept", "application/json")
        .build()

    private fun isValidJson(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        if (body.contains("\"error\"") || body.contains("shutdown") || body.contains("CAPTCHA")) return false
        if (body.contains("<!DOCTYPE") || body.contains("<html") || body.contains("Redirecting")) return false
        return body.trimStart().startsWith("[") || body.trimStart().startsWith("{")
    }

    private fun executeRequest(url: String, client: OkHttpClient = okHttpClient): Pair<Int, String?> {
        val response = client.newCall(buildRequest(url)).execute()
        return response.use { Pair(it.code, it.body?.string()) }
    }

    private fun executePostRequest(url: String, jsonBody: String, contentType: String = "application/json", userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"): Pair<Int, String?> {
        val body = jsonBody.toRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", userAgent)
            .header("Content-Type", contentType)
            .build()
        val response = okHttpClient.newCall(request).execute()
        return response.use { Pair(it.code, it.body?.string()) }
    }

    private data class YouTubeClient(
        val name: String,
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val clientMap: Map<String, Any>
    )

    private val youtubeClients = listOf(
        YouTubeClient(
            name = "ANDROID_VR",
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; en_US; Quest 3 Build/SQ3A.220605.009.A1)",
            clientMap = mapOf(
                "clientName" to "ANDROID_VR",
                "clientVersion" to "1.60.19",
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "hl" to "en",
                "gl" to "US",
                "osName" to "Android",
                "osVersion" to "12L"
            )
        ),
        YouTubeClient(
            name = "ANDROID",
            clientName = "ANDROID",
            clientVersion = "19.44.38",
            userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14; en_US; sdk_gphone64_x86_64 Build/UE1A.230829.036.A1) gzip",
            clientMap = mapOf(
                "clientName" to "ANDROID",
                "clientVersion" to "19.44.38",
                "androidSdkVersion" to 30,
                "hl" to "en",
                "gl" to "US",
                "osName" to "Android",
                "osVersion" to "14"
            )
        ),
        YouTubeClient(
            name = "IOS",
            clientName = "IOS",
            clientVersion = "19.45.4",
            userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
            clientMap = mapOf(
                "clientName" to "IOS",
                "clientVersion" to "19.45.4",
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "hl" to "en",
                "gl" to "US",
                "osName" to "iPhone",
                "osVersion" to "18.1.0.22B83"
            )
        ),
        YouTubeClient(
            name = "WEB",
            clientName = "WEB",
            clientVersion = "2.20250623.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            clientMap = mapOf(
                "clientName" to "WEB",
                "clientVersion" to "2.20250623.01.00",
                "hl" to "en",
                "gl" to "US"
            )
        )
    )

    private fun getStreamsFromYouTube(videoId: String): Stream? {
        for (client in youtubeClients) {
            val result = getStreamsFromYouTubeWithClient(videoId, client)
            if (result != null) return result
        }
        return null
    }

    private fun getStreamsFromYouTubeWithClient(videoId: String, client: YouTubeClient): Stream? {
        try {
            val innertubeBody = gson.toJson(mapOf(
                "videoId" to videoId,
                "context" to mapOf("client" to client.clientMap),
                "contentCheckOk" to true,
                "racyCheckOk" to true
            ))

            val url = when (client.name) {
                "WEB", "WEB_EMBEDDED" -> "https://www.youtube.com/youtubei/v1/player?prettyPrint=false&key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
                else -> "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            }
            
            val (code, body) = executePostRequest(
                url,
                innertubeBody,
                userAgent = client.userAgent
            )

            if (code != 200 || body == null) {
                CrashLogger.d("YouTube innertube ${client.name} returned $code for $videoId")
                return null
            }

            val json = gson.fromJson(body, JsonObject::class.java) ?: return null

            val playabilityStatus = json.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            if (status != "OK") {
                val reason = playabilityStatus?.get("reason")?.asString ?: "unknown"
                CrashLogger.d("YouTube innertube ${client.name} playability: $status ($reason) for $videoId")
                return null
            }

            val streamingData = json.getAsJsonObject("streamingData") ?: return null
            val videoDetails = json.getAsJsonObject("videoDetails")

            val title = videoDetails?.get("title")?.asString ?: "Unknown"
            val author = videoDetails?.get("author")?.asString ?: "Unknown"
            val lengthSeconds = videoDetails?.get("lengthSeconds")?.asString?.toLongOrNull()
            val viewCount = videoDetails?.get("viewCount")?.asString?.toLongOrNull()
            val thumbnailUrl = videoDetails?.get("thumbnail")?.let { thumb ->
                thumb.asJsonObject.getAsJsonArray("thumbnails")?.lastOrNull()?.let {
                    it.asJsonObject.get("url")?.asString
                }
            }

            val videoStreams = mutableListOf<StreamFormat>()
            val audioStreams = mutableListOf<StreamFormat>()

            streamingData.getAsJsonArray("formats")?.forEach { element ->
                val format = element.asJsonObject
                val formatUrl = format.get("url")?.asString
                if (formatUrl != null) {
                    videoStreams.add(StreamFormat(
                        url = formatUrl,
                        quality = format.get("qualityLabel")?.asString,
                        mimeType = format.get("mimeType")?.asString,
                        codec = null,
                        bitrate = format.get("bitrate")?.asLong,
                        width = format.get("width")?.asInt,
                        height = format.get("height")?.asInt,
                        fps = format.get("fps")?.asInt
                    ))
                }
            }

            streamingData.getAsJsonArray("adaptiveFormats")?.forEach { element ->
                val format = element.asJsonObject
                val formatUrl = format.get("url")?.asString
                val mimeType = format.get("mimeType")?.asString ?: ""
                if (formatUrl != null) {
                    val streamFormat = StreamFormat(
                        url = formatUrl,
                        quality = format.get("qualityLabel")?.asString,
                        mimeType = mimeType,
                        codec = null,
                        bitrate = format.get("bitrate")?.asLong,
                        width = format.get("width")?.asInt,
                        height = format.get("height")?.asInt,
                        fps = format.get("fps")?.asInt
                    )
                    if (mimeType.startsWith("audio")) {
                        audioStreams.add(streamFormat)
                    } else {
                        videoStreams.add(streamFormat)
                    }
                }
            }

            CrashLogger.d("YouTube innertube ${client.name}: $title - ${videoStreams.size} video, ${audioStreams.size} audio streams")

            return Stream(
                title = title,
                uploader = author,
                uploaderUrl = null,
                thumbnailUrl = thumbnailUrl,
                duration = lengthSeconds,
                views = viewCount,
                uploaded = null,
                uploadDate = null,
                description = videoDetails?.get("shortDescription")?.asString,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                livestream = false,
                subtitles = emptyList()
            )
        } catch (e: Exception) {
            CrashLogger.d("YouTube innertube ${client.name} failed for $videoId: ${e.message}")
            return null
        }
    }

    suspend fun getTrending(): Result<List<StreamItem>> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30_000L) {
            try {
                var lastException: Exception? = null

                // Signed in? Show the account's personalized home ("What to watch") first.
                val personalizedHome = getPersonalizedHomeFromYouTube()
                if (!personalizedHome.isNullOrEmpty()) {
                    CrashLogger.d("Personalized YouTube home: ${personalizedHome.size} items")
                    return@withTimeoutOrNull Result.success(personalizedHome)
                }

                CrashLogger.d("Trying NewPipeExtractor trending first")
                val newPipeTrending = newPipeSource.getTrending()
                if (newPipeTrending.isNotEmpty()) {
                    CrashLogger.d("NewPipe trending: ${newPipeTrending.size} items")
                    return@withTimeoutOrNull Result.success(newPipeTrending)
                }

                CrashLogger.d("Trying YouTube trending with ANDROID_VR client")
                val ytTrending = getTrendingFromYouTube()
                if (ytTrending != null && ytTrending.isNotEmpty()) {
                    CrashLogger.d("YouTube trending success: ${ytTrending.size} items")
                    return@withTimeoutOrNull Result.success(ytTrending)
                }

                CrashLogger.d("YouTube trending failed, trying Piped trending")
                for (instance in getOrderedPipedInstances()) {
                    val baseUrl = instance.url
                    try {
                        val url = "${baseUrl.trimEnd('/')}/trending"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(baseUrl, latency)
                            val parsed = gson.fromJson(body, JsonObject::class.java)
                            val items = if (parsed != null && parsed.has("items")) {
                                val trendingResponse = gson.fromJson(body, PipedTrendingResponse::class.java)
                                trendingResponse?.items.orEmpty().map { it.toStreamItem() }
                            } else {
                                gson.fromJson(body, Array<PipedTrendingItem>::class.java)
                                    ?.map { it.toStreamItem() }?.toList() ?: emptyList()
                            }
                            if (items.isNotEmpty()) {
                                CrashLogger.d("Piped trending from $baseUrl: ${items.size} items")
                                return@withTimeoutOrNull Result.success(items)
                            }
                        }
                        CrashLogger.d("Piped trending $baseUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Piped trending $baseUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(baseUrl)
                    }
                }

                CrashLogger.d("Piped trending exhausted, falling back to Invidious")
                for (instance in getOrderedInvidiousInstances()) {
                    val invidiousUrl = instance.url
                    try {
                        val url = "${invidiousUrl.trimEnd('/')}/api/v1/trending"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(invidiousUrl, latency)
                            val items = gson.fromJson(body, Array<InvidiousSearchItem>::class.java)
                                ?.map { it.toStreamItem() } ?: emptyList()
                            if (items.isNotEmpty()) {
                                CrashLogger.d("Invidious trending from $invidiousUrl: ${items.size} items")
                                return@withTimeoutOrNull Result.success(items)
                            }
                        }
                        CrashLogger.d("Invidious trending $invidiousUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Invidious trending $invidiousUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(invidiousUrl)
                    }
                }

                Result.failure(lastException ?: Exception("No trending providers available"))
            } catch (e: Exception) {
                CrashLogger.e("getTrending fatal error", e)
                Result.failure(Exception("Failed to load trending: ${e.message}"))
            }
        }
        result ?: Result.failure(Exception("Trending request timed out"))
    }

    suspend fun getMusicTrending(): Result<List<StreamItem>> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30_000L) {
            try {
                var lastException: Exception? = null

                // PRIMARY: real YouTube Music feed (WEB_REMIX explore/charts). This is a genuinely
                // different, music-only feed — not the video-trending list filtered by keyword.
                CrashLogger.d("Trying YouTube Music feed first")
                // FEmusic_home = personalized (needs sign-in); explore/charts work anonymously.
                val musicFeed = getMusicFeedFromYouTube("FEmusic_home")
                    ?: getMusicFeedFromYouTube("FEmusic_explore")
                    ?: getMusicFeedFromYouTube("FEmusic_charts")
                if (!musicFeed.isNullOrEmpty()) {
                    CrashLogger.d("YT Music feed: ${musicFeed.size} items")
                    return@withTimeoutOrNull Result.success(musicFeed)
                }

                CrashLogger.d("Trying YouTube music trending fallback")
                val ytMusic = getMusicTrendingFromYouTube()
                if (ytMusic != null && ytMusic.isNotEmpty()) {
                    return@withTimeoutOrNull Result.success(ytMusic)
                }

                CrashLogger.d("YouTube music trending failed, trying Piped")
                for (instance in getOrderedPipedInstances()) {
                    val baseUrl = instance.url
                    try {
                        val url = "${baseUrl.trimEnd('/')}/trending?features=music"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(baseUrl, latency)
                            val parsed = gson.fromJson(body, JsonObject::class.java)
                            val items = if (parsed != null && parsed.has("items")) {
                                val trendingResponse = gson.fromJson(body, PipedTrendingResponse::class.java)
                                trendingResponse?.items.orEmpty().map { it.toStreamItem() }
                            } else {
                                gson.fromJson(body, Array<PipedTrendingItem>::class.java)
                                    ?.map { it.toStreamItem() }?.toList() ?: emptyList()
                            }
                            if (items.isNotEmpty()) {
                                CrashLogger.d("Piped music trending from $baseUrl: ${items.size} items")
                                return@withTimeoutOrNull Result.success(items)
                            }
                        }
                        CrashLogger.d("Piped music trending $baseUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Piped music trending $baseUrl failed: ${e.message}")
                        instanceManager.recordFailure(baseUrl)
                    }
                }

                CrashLogger.d("Piped music trending exhausted, falling back to Invidious")
                for (instance in getOrderedInvidiousInstances()) {
                    val invidiousUrl = instance.url
                    try {
                        val url = "${invidiousUrl.trimEnd('/')}/api/v1/trending?features=music"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(invidiousUrl, latency)
                            val items = gson.fromJson(body, Array<InvidiousSearchItem>::class.java)
                                ?.map { it.toStreamItem() } ?: emptyList()
                            if (items.isNotEmpty()) {
                                CrashLogger.d("Invidious music trending from $invidiousUrl: ${items.size} items")
                                return@withTimeoutOrNull Result.success(items)
                            }
                        }
                        CrashLogger.d("Invidious music trending $invidiousUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Invidious music trending $invidiousUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(invidiousUrl)
                    }
                }

                Result.failure(lastException ?: Exception("No music providers available"))
            } catch (e: Exception) {
                CrashLogger.e("getMusicTrending fatal error", e)
                Result.failure(Exception("Failed to load music trending: ${e.message}"))
            }
        }
        result ?: Result.failure(Exception("Music trending request timed out"))
    }

    /**
     * Builds SAPISIDHASH auth headers from the WebView cookie jar (set by the Settings sign-in).
     * Returns null when the user isn't signed in, so callers transparently fall back to anonymous.
     */
    private fun youtubeAuthHeaders(origin: String): Map<String, String>? {
        return try {
            // Prefer the cookies captured at login (reliable); fall back to CookieManager.
            val cookies = preferencesManager.youtubeCookiesBlocking()
                ?: CookieManager.getInstance().getCookie(origin)
                ?: CookieManager.getInstance().getCookie("https://www.youtube.com")
            CrashLogger.d("auth[$origin]: cookieLen=${cookies?.length ?: -1}")
            if (cookies == null) return null
            val sapisid = parseCookieValue(cookies, "SAPISID")
                ?: parseCookieValue(cookies, "__Secure-3PAPISID")
            CrashLogger.d("auth[$origin]: sapisid=${sapisid != null}")
            if (sapisid == null) return null
            val ts = System.currentTimeMillis() / 1000
            val digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest("$ts $sapisid $origin".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            mapOf(
                "Cookie" to cookies,
                "Authorization" to "SAPISIDHASH ${ts}_$digest",
                "X-Goog-AuthUser" to "0",
                "Origin" to origin
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCookieValue(cookies: String, name: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Personalized YouTube home ("What to watch", `FEwhat_to_watch`) via the WEB InnerTube client
     * with SAPISIDHASH auth. Returns null when signed out (so getTrending falls back to anonymous
     * NewPipe trending). Walks the response tree collecting every videoRenderer.
     */
    private fun getPersonalizedHomeFromYouTube(): List<StreamItem>? {
        val auth = youtubeAuthHeaders("https://www.youtube.com") ?: return null
        return try {
            val bodyJson = gson.toJson(mapOf(
                "browseId" to "FEwhat_to_watch",
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB",
                        "clientVersion" to "2.20240726.00.00",
                        "hl" to "en",
                        "gl" to "US"
                    )
                )
            ))
            val builder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
            auth.forEach { (k, v) -> builder.header(k, v) }

            val response = okHttpClient.newCall(builder.build()).execute()
            val code = response.code
            val respBody = response.use { it.body?.string() }
            if (code != 200 || respBody == null || !isValidJson(respBody)) {
                CrashLogger.d("Personalized home -> $code")
                return null
            }
            val root = gson.fromJson(respBody, JsonObject::class.java) ?: return null
            val items = mutableListOf<StreamItem>()
            collectVideoRenderers(root, items)

            // Load more pages via continuation so the feed isn't thin.
            var token = findContinuationToken(root)
            var pages = 0
            while (token != null && items.size < 40 && pages < 3) {
                val (more, next) = fetchBrowseContinuation(token, auth)
                if (more.isEmpty()) break
                items.addAll(more)
                token = next
                pages++
            }
            CrashLogger.d("Personalized home collected ${items.size} raw items over ${pages + 1} pages")
            items.distinctBy { it.videoId }.filter { it.videoId.isNotBlank() }.take(60).ifEmpty { null }
        } catch (e: Exception) {
            CrashLogger.d("Personalized home failed: ${e.message}")
            null
        }
    }

    private val VIDEO_RENDERER_KEYS =
        listOf("videoRenderer", "gridVideoRenderer", "compactVideoRenderer")

    private val AD_RENDERER_KEYS = setOf(
        "promotedSparklesWebRenderer",
        "promotedVideoRenderer",
        "adPlacementRenderer",
        "playerOverlayAdRenderer",
        "searchAdRenderer",
        "bannerPromoRenderer",
        "promotedBannerRenderer",
        "inFeedAdRenderer",
        "brandVideoShelfRenderer",
        "brandVideoSingletonShelfRenderer"
    )

    private fun isAdItem(obj: JsonObject): Boolean {
        for (key in AD_RENDERER_KEYS) {
            if (obj.has(key)) return true
        }
        // "metadata"/"badges" are sometimes arrays — never getAsJsonObject them blindly.
        val metadata = obj.get("metadata")
        if (metadata != null && metadata.isJsonObject &&
            metadata.asJsonObject.get("externalSearchSource") != null
        ) return true
        val badges = obj.get("badges")
        if (badges != null) {
            val label = badges.toString().lowercase()
            // A bare "ad" substring would match "metadataBadgeRenderer" and nuke LIVE/New
            // badges too — match only real ad badge markers.
            if ("sponsored" in label || "badge_style_type_ad" in label) return true
        }
        return false
    }

    private fun collectVideoRenderers(
        element: com.google.gson.JsonElement,
        out: MutableList<StreamItem>
    ) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (isAdItem(obj)) return
                for (key in VIDEO_RENDERER_KEYS) {
                    obj.getAsJsonObject(key)?.let { parseVideoRenderer(it)?.let(out::add) }
                }
                obj.getAsJsonObject("reelItemRenderer")?.let { parseReelRenderer(it)?.let(out::add) }
                for ((_, v) in obj.entrySet()) collectVideoRenderers(v, out)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectVideoRenderers(it, out) }
        }
    }

    private fun parseReelRenderer(r: JsonObject): StreamItem? {
        val videoId = r.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("reelWatchEndpoint")?.get("videoId")?.asString ?: return null
        val title = r.getAsJsonObject("headline")?.get("simpleText")?.asString
            ?: r.getAsJsonObject("headline")?.getAsJsonArray("runs")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString
            ?: "Short"
        return musicItem(videoId, title, "Shorts")
    }

    /** Recursively find the first continuation token in a browse/next response. */
    private fun findContinuationToken(element: com.google.gson.JsonElement): String? {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.getAsJsonObject("continuationCommand")?.get("token")?.asString?.let { return it }
                obj.getAsJsonObject("continuationEndpoint")
                    ?.getAsJsonObject("continuationCommand")?.get("token")?.asString?.let { return it }
                for ((_, v) in obj.entrySet()) findContinuationToken(v)?.let { return it }
            }
            element.isJsonArray -> element.asJsonArray.forEach { child ->
                findContinuationToken(child)?.let { return it }
            }
        }
        return null
    }

    private fun fetchBrowseContinuation(
        token: String,
        auth: Map<String, String>
    ): Pair<List<StreamItem>, String?> {
        return try {
            val bodyJson = gson.toJson(mapOf(
                "continuation" to token,
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB",
                        "clientVersion" to "2.20240726.00.00",
                        "hl" to "en",
                        "gl" to "US"
                    )
                )
            ))
            val builder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
            auth.forEach { (k, v) -> builder.header(k, v) }
            val response = okHttpClient.newCall(builder.build()).execute()
            val code = response.code
            val respBody = response.use { it.body?.string() }
            if (code != 200 || respBody == null || !isValidJson(respBody)) return emptyList<StreamItem>() to null
            val root = gson.fromJson(respBody, JsonObject::class.java) ?: return emptyList<StreamItem>() to null
            val items = mutableListOf<StreamItem>()
            collectVideoRenderers(root, items)
            items to findContinuationToken(root)
        } catch (e: Exception) {
            emptyList<StreamItem>() to null
        }
    }

    private fun parseVideoRenderer(v: JsonObject): StreamItem? {
        val videoId = v.get("videoId")?.asString ?: return null
        if (v.has("promotedContent") || v.has("adSlot")) return null
        val title = v.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()
            ?.asJsonObject?.get("text")?.asString
            ?: v.getAsJsonObject("title")?.get("simpleText")?.asString
            ?: "Unknown"
        val channel = v.getAsJsonObject("ownerText")?.getAsJsonArray("runs")?.firstOrNull()
            ?.asJsonObject?.get("text")?.asString
            ?: v.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString
            ?: "Unknown"
        val viewText = v.getAsJsonObject("viewCountText")?.get("simpleText")?.asString
        val views = viewText?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0L
        val lengthText = v.getAsJsonObject("lengthText")?.get("simpleText")?.asString
        return StreamItem(
            url = "/watch?v=$videoId",
            videoId = videoId,
            title = title,
            thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            uploaderName = channel,
            uploaderUrl = null,
            uploaderAvatar = null,
            views = views,
            duration = parseDuration(lengthText),
            uploadedDate = null,
            uploaded = null
        )
    }

    /**
     * Fetches a real YouTube Music feed via the WEB_REMIX InnertTube client. `FEmusic_explore`
     * (new releases / moods / trending videos) and `FEmusic_charts` both return content anonymously.
     * Response is deeply nested + heterogeneous, so we walk the tree and collect every
     * musicResponsiveListItemRenderer (list rows) and musicTwoRowItemRenderer (cards) with a videoId.
     */
    private fun getMusicFeedFromYouTube(browseId: String): List<StreamItem>? {
        val root = musicBrowseRoot(browseId) ?: return null
        val items = mutableListOf<StreamItem>()
        collectMusicItems(root, items)
        return items.distinctBy { it.videoId }.filter { it.videoId.isNotBlank() }.take(60).ifEmpty { null }
    }

    /** Executes a WEB_REMIX InnerTube browse and returns the parsed JSON root (null on any failure). */
    private fun musicBrowseRoot(browseId: String): JsonObject? {
        return try {
            val clientVersion = "1." +
                java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) +
                ".01.00"
            val bodyJson = gson.toJson(mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to clientVersion,
                        "hl" to "en",
                        "gl" to "US"
                    ),
                    "user" to emptyMap<String, Any>()
                ),
                "browseId" to browseId
            ))

            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
            // When signed in, attach SAPISIDHASH auth so FEmusic_home returns a personalized feed.
            youtubeAuthHeaders("https://music.youtube.com")?.forEach { (k, v) -> builder.header(k, v) }

            val response = okHttpClient.newCall(builder.build()).execute()
            val code = response.code
            val respBody = response.use { it.body?.string() }

            if (code != 200 || respBody == null || !isValidJson(respBody)) {
                CrashLogger.d("YT Music browse $browseId -> $code")
                return null
            }
            gson.fromJson(respBody, JsonObject::class.java)
        } catch (e: Exception) {
            CrashLogger.d("YT Music browse $browseId failed: ${e.message}")
            null
        }
    }

    /**
     * Sectioned YouTube Music feed: preserves the real shelf structure ("Listen again",
     * "New releases", "Charts", …) instead of flattening everything into one list.
     * Tries the personalized home first, then explore, then charts; a flat trending
     * result is wrapped into a single section as last resort.
     */
    suspend fun getMusicSections(): Result<List<MusicSection>> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30_000L) {
            // Anonymous FEmusic_home is often a single shelf — top it up with explore/charts
            // shelves (deduped by title) so the home feels as rich as the real YT Music.
            val sections = mutableListOf<MusicSection>()
            for (browseId in listOf("FEmusic_home", "FEmusic_explore", "FEmusic_charts")) {
                if (sections.size >= 4) break
                val root = musicBrowseRoot(browseId) ?: continue
                val fresh = collectMusicSections(root)
                    .filter { s -> sections.none { it.title.equals(s.title, ignoreCase = true) } }
                sections.addAll(fresh)
                CrashLogger.d("YT Music sections[$browseId]: +${fresh.size} shelves (total ${sections.size})")
            }
            if (sections.isNotEmpty()) {
                Result.success(sections.take(12))
            } else {
                getMusicTrending().map { items -> listOf(MusicSection("Trending", items)) }
            }
        }
        result ?: Result.failure(Exception("Music sections request timed out"))
    }

    /** Explore feed (new releases / moods / charts), never personalized. */
    suspend fun getMusicExploreSections(): Result<List<MusicSection>> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30_000L) {
            for (browseId in listOf("FEmusic_explore", "FEmusic_charts", "FEmusic_new_releases")) {
                val root = musicBrowseRoot(browseId) ?: continue
                val sections = collectMusicSections(root)
                if (sections.isNotEmpty()) {
                    CrashLogger.d("YT Music explore[$browseId]: ${sections.size} shelves")
                    return@withTimeoutOrNull Result.success(sections)
                }
            }
            Result.failure<List<MusicSection>>(Exception("No explore feed available"))
        }
        result ?: Result.failure(Exception("Music explore request timed out"))
    }

    private fun collectMusicSections(root: JsonObject): List<MusicSection> {
        val sections = mutableListOf<MusicSection>()
        collectMusicShelves(root, sections)
        return sections
            .map { s -> s.copy(items = s.items.distinctBy { it.videoId }.filter { it.videoId.isNotBlank() }) }
            .filter { it.items.isNotEmpty() }
            .take(12)
    }

    private fun collectMusicShelves(
        element: com.google.gson.JsonElement,
        out: MutableList<MusicSection>
    ) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.getAsJsonObject("musicCarouselShelfRenderer")?.let { shelf ->
                    val title = shelf.getAsJsonObject("header")
                        ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()
                        ?.asJsonObject?.get("text")?.asString ?: "Music"
                    val items = mutableListOf<StreamItem>()
                    shelf.getAsJsonArray("contents")?.let { collectMusicItems(it, items) }
                    if (items.isNotEmpty()) out.add(MusicSection(title, items))
                }
                obj.getAsJsonObject("musicShelfRenderer")?.let { shelf ->
                    val title = shelf.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()
                        ?.asJsonObject?.get("text")?.asString ?: "Music"
                    val items = mutableListOf<StreamItem>()
                    shelf.getAsJsonArray("contents")?.let { collectMusicItems(it, items) }
                    if (items.isNotEmpty()) out.add(MusicSection(title, items))
                }
                for ((_, v) in obj.entrySet()) collectMusicShelves(v, out)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectMusicShelves(it, out) }
        }
    }

    private fun collectMusicItems(
        element: com.google.gson.JsonElement,
        out: MutableList<StreamItem>
    ) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val isAd = obj.has("promotedContent") ||
                    obj.has("adSlot") ||
                    obj.toString().contains("\"isAd\":true", ignoreCase = true)
                if (!isAd) {
                    obj.getAsJsonObject("musicResponsiveListItemRenderer")?.let { parseMrlir(it)?.let(out::add) }
                    obj.getAsJsonObject("musicTwoRowItemRenderer")?.let { parseMtrir(it)?.let(out::add) }
                }
                for ((_, v) in obj.entrySet()) collectMusicItems(v, out)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectMusicItems(it, out) }
        }
    }

    private fun parseMtrir(r: JsonObject): StreamItem? {
        val videoId = r.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString ?: return null
        val title = r.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()
            ?.asJsonObject?.get("text")?.asString ?: "Unknown"
        val artist = r.getAsJsonObject("subtitle")?.getAsJsonArray("runs")?.firstOrNull()
            ?.asJsonObject?.get("text")?.asString ?: "Unknown"
        return musicItem(videoId, title, artist)
    }

    private fun parseMrlir(r: JsonObject): StreamItem? {
        val flex = r.getAsJsonArray("flexColumns") ?: return null
        val col0 = flex.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
        val titleRun = col0?.getAsJsonObject("text")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject
        val title = titleRun?.get("text")?.asString ?: "Unknown"

        var videoId = titleRun?.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
        if (videoId == null) {
            videoId = r.getAsJsonObject("overlay")
                ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("musicPlayButtonRenderer")
                ?.getAsJsonObject("playNavigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
        }
        if (videoId == null) {
            videoId = r.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
        }
        if (videoId.isNullOrBlank()) return null

        val artist = (if (flex.size() > 1) flex.get(1) else null)?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            ?.getAsJsonObject("text")?.getAsJsonArray("runs")?.firstOrNull()
            ?.asJsonObject?.get("text")?.asString ?: "Unknown"
        return musicItem(videoId, title, artist)
    }

    private fun musicItem(videoId: String, title: String, artist: String) = StreamItem(
        url = "/watch?v=$videoId",
        videoId = videoId,
        title = title,
        thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        uploaderName = artist,
        uploaderUrl = null,
        uploaderAvatar = null,
        views = 0,
        duration = null,
        uploadedDate = null,
        uploaded = null
    )

    private fun getMusicTrendingFromYouTube(): List<StreamItem>? {
        try {
            val body = gson.toJson(mapOf(
                "browseId" to "UC-9-kyTW8ZkZNDHQJ6FgpwQ",
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB",
                        "clientVersion" to "2.20240726.00.00",
                        "hl" to "en",
                        "gl" to "US"
                    )
                )
            ))

            val (code, response) = executePostRequest(
                "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                body,
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )

            if (code != 200 || response == null) {
                CrashLogger.d("YouTube music browse returned $code")
                return null
            }

            val json = gson.fromJson(response, JsonObject::class.java) ?: return null
            val contents = json.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")?.firstOrNull()
                ?.asJsonObject?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return null

            val results = mutableListOf<StreamItem>()
            for (section in contents) {
                val items = section.asJsonObject
                    ?.getAsJsonObject("itemSectionRenderer")
                    ?.getAsJsonArray("contents") ?: continue
                for (item in items) {
                    val video = item.asJsonObject?.getAsJsonObject("videoRenderer") ?: continue
                    val videoId = video.get("videoId")?.asString ?: continue
                    val title = video.getAsJsonObject("title")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"
                    val channelName = video.getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"
                    val viewCountText = video.getAsJsonObject("viewCountText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString
                    val viewCount = viewCountText?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0
                    val lengthText = video.getAsJsonObject("lengthText")?.get("simpleText")?.asString
                    val duration = parseDuration(lengthText)
                    val thumbnail = video.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                        ?.lastOrNull()?.asJsonObject?.get("url")?.asString
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    results.add(StreamItem(
                        url = "/watch?v=$videoId",
                        videoId = videoId,
                        title = title,
                        thumbnail = thumbnail,
                        uploaderName = channelName,
                        uploaderUrl = null,
                        uploaderAvatar = null,
                        views = viewCount,
                        duration = duration,
                        uploadedDate = null,
                        uploaded = null
                    ))
                }
            }

            CrashLogger.d("YouTube music trending: ${results.size} results")
            return results.ifEmpty { null }
        } catch (e: Exception) {
            CrashLogger.d("YouTube music trending failed: ${e.message}")
            return null
        }
    }

    suspend fun search(query: String): Result<List<StreamItem>> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30_000L) {
            try {
                var lastException: Exception? = null

                CrashLogger.d("Trying NewPipeExtractor search first for '$query'")
                val newPipeResults = newPipeSource.search(query)
                if (newPipeResults.isNotEmpty()) {
                    CrashLogger.d("NewPipe search '$query': ${newPipeResults.size} results")
                    return@withTimeoutOrNull Result.success(newPipeResults)
                }

                CrashLogger.d("Trying YouTube innertube search for '$query'")
                val ytResults = searchYouTube(query)
                if (ytResults != null && ytResults.isNotEmpty()) {
                    return@withTimeoutOrNull Result.success(ytResults)
                }

                CrashLogger.d("YouTube search failed for '$query', trying Piped")
                for (instance in getOrderedPipedInstances()) {
                    val baseUrl = instance.url
                    try {
                        val url = "${baseUrl.trimEnd('/')}/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&filter=videos"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(baseUrl, latency)
                            val parsed = gson.fromJson(body, com.google.gson.JsonElement::class.java)
                            val items = when {
                                parsed.isJsonArray -> parsed.asJsonArray.mapNotNull {
                                    gson.fromJson(it, PipedSearchItem::class.java).toStreamItem()
                                }
                                parsed.isJsonObject -> {
                                    val arr = parsed.asJsonObject.getAsJsonArray("items")
                                    arr?.mapNotNull {
                                        gson.fromJson(it, PipedSearchItem::class.java).toStreamItem()
                                    } ?: emptyList()
                                }
                                else -> emptyList()
                            }
                            if (items.isNotEmpty()) {
                                CrashLogger.d("Piped search '$query' from $baseUrl: ${items.size} results")
                                return@withTimeoutOrNull Result.success(items)
                            }
                        }
                        CrashLogger.d("Piped search $baseUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Piped search $baseUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(baseUrl)
                    }
                }

                CrashLogger.e("All Piped search failed, trying Invidious", lastException ?: Exception("No instances"))
                for (instance in getOrderedInvidiousInstances()) {
                    val invidiousUrl = instance.url
                    try {
                        val url = "${invidiousUrl.trimEnd('/')}/api/v1/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=video"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(invidiousUrl, latency)
                            val items = gson.fromJson(body, Array<InvidiousSearchItem>::class.java)
                                ?.map { it.toStreamItem() } ?: emptyList()
                            if (items.isNotEmpty()) {
                                CrashLogger.d("Invidious search '$query' from $invidiousUrl: ${items.size} results")
                                return@withTimeoutOrNull Result.success(items)
                            }
                        }
                        CrashLogger.d("Invidious search $invidiousUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Invidious search $invidiousUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(invidiousUrl)
                    }
                }

                Result.failure(lastException ?: Exception("No search providers available"))
            } catch (e: Exception) {
                CrashLogger.e("search fatal error for '$query'", e)
                Result.failure(Exception("Search failed for '$query': ${e.message}"))
            }
        }
        result ?: Result.failure(Exception("Search request timed out for '$query'"))
    }

    suspend fun getStreams(videoId: String): Result<Stream> = withContext(Dispatchers.IO) {
        // Longer timeout: the first stream load also spins up the poToken WebView + BotGuard
        // (one-time, ~10-15s). Subsequent loads reuse the cached generator.
        val result = withTimeoutOrNull(45_000L) {
            try {
                var lastException: Exception? = null

                // PRIMARY: NewPipeExtractor (client-side JS sig/nsig deciphering).
                // This is the only source that reliably returns playable high-quality
                // adaptive streams; Piped/Invidious/InnerTube below are fallbacks.
                CrashLogger.d("Trying NewPipeExtractor first for $videoId")
                val newPipeStream = newPipeSource.getStream(videoId)
                if (newPipeStream != null) {
                    val hasPlayable = newPipeStream.videoStreams.any { !it.url.isNullOrBlank() } ||
                        newPipeStream.audioStreams.any { !it.url.isNullOrBlank() } ||
                        !newPipeStream.hlsManifestUrl.isNullOrBlank() ||
                        !newPipeStream.dashManifestUrl.isNullOrBlank()
                    if (hasPlayable) {
                        CrashLogger.d("NewPipe success for $videoId: ${newPipeStream.videoStreams.size} video, ${newPipeStream.audioStreams.size} audio")
                        return@withTimeoutOrNull Result.success(newPipeStream)
                    }
                }

                CrashLogger.d("NewPipe failed for $videoId, trying Piped")
                for (instance in getOrderedPipedInstances()) {
                    val baseUrl = instance.url
                    try {
                        val url = "${baseUrl.trimEnd('/')}/streams/$videoId"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(baseUrl, latency)
                            val videoResponse = gson.fromJson(body, PipedVideoResponse::class.java)
                            val stream = videoResponse.toStream()
                            val hasPlayableVideo = stream.videoStreams.any { !it.url.isNullOrBlank() }
                            val hasPlayableAudio = stream.audioStreams.any { !it.url.isNullOrBlank() }
                            CrashLogger.d("Piped streams for $videoId from $baseUrl: ${stream.videoStreams.size} video (${if (hasPlayableVideo) "playable" else "none"}), ${stream.audioStreams.size} audio (${if (hasPlayableAudio) "playable" else "none"})")
                            if (hasPlayableVideo || hasPlayableAudio) {
                                return@withTimeoutOrNull Result.success(stream)
                            }
                            CrashLogger.d("Piped $baseUrl returned empty/unplayable streams, trying next")
                        } else {
                            CrashLogger.d("Piped $baseUrl returned $code (invalid response)")
                        }
                    } catch (e: Exception) {
                        CrashLogger.d("Piped $baseUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(baseUrl)
                    }
                }

                CrashLogger.d("Piped failed for $videoId, trying YouTube innertube")
                val ytStream = getStreamsFromYouTube(videoId)
                if (ytStream != null) {
                    return@withTimeoutOrNull Result.success(ytStream)
                }

                CrashLogger.d("All Piped and YouTube failed for $videoId, trying Invidious")
                for (instance in getOrderedInvidiousInstances()) {
                    val invidiousUrl = instance.url
                    try {
                        val url = "${invidiousUrl.trimEnd('/')}/api/v1/videos/$videoId?local=true"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(invidiousUrl, latency)
                            val videoResponse = gson.fromJson(body, InvidiousVideoResponse::class.java)
                            val stream = videoResponse.toStream()
                            val hasPlayableVideo = stream.videoStreams.any { !it.url.isNullOrBlank() }
                            val hasPlayableAudio = stream.audioStreams.any { !it.url.isNullOrBlank() }
                            CrashLogger.d("Invidious streams for $videoId from $invidiousUrl: ${stream.videoStreams.size} video (${if (hasPlayableVideo) "playable" else "none"}), ${stream.audioStreams.size} audio (${if (hasPlayableAudio) "playable" else "none"})")
                            if (hasPlayableVideo || hasPlayableAudio) {
                                return@withTimeoutOrNull Result.success(stream)
                            }
                            CrashLogger.d("Invidious $invidiousUrl returned empty/unplayable streams, trying next")
                        }
                        CrashLogger.d("Invidious $invidiousUrl returned $code (invalid response)")
                    } catch (e: Exception) {
                        CrashLogger.d("Invidious $invidiousUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(invidiousUrl)
                    }
                }

                Result.failure(lastException ?: Exception("No working video providers found. Try again later or check your connection."))
            } catch (e: Exception) {
                Result.failure(Exception("Failed to load streams for $videoId: ${e.message}"))
            }
        }
        result ?: Result.failure(Exception("Stream request timed out for $videoId"))
    }

    suspend fun getRelatedStreams(videoId: String): Result<List<StreamItem>> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30_000L) {
            try {
                var lastException: Exception? = null

                CrashLogger.d("Trying YouTube innertube /next for related streams: $videoId")
                val ytRelated = getRelatedFromYouTube(videoId)
                if (ytRelated != null && ytRelated.isNotEmpty()) {
                    CrashLogger.d("YouTube innertube /next related for $videoId: ${ytRelated.size} items")
                    return@withTimeoutOrNull Result.success(ytRelated)
                }

                CrashLogger.d("YouTube /next failed, trying Piped related streams")
                for (instance in getOrderedPipedInstances()) {
                    val baseUrl = instance.url
                    try {
                        val url = "${baseUrl.trimEnd('/')}/streams/$videoId"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(baseUrl, latency)
                            val parsed = gson.fromJson(body, JsonObject::class.java)
                            val related = parsed?.getAsJsonArray("relatedStreams")
                            if (related != null && related.size() > 0) {
                                val items = related.mapNotNull { el ->
                                    val obj = el.asJsonObject ?: return@mapNotNull null
                                    val vid = obj.get("url")?.asString?.removePrefix("/watch?v=") ?: return@mapNotNull null
                                    val title = obj.get("title")?.asString ?: "Unknown"
                                    val thumb = obj.get("thumbnail")?.asString ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                    val channel = obj.get("uploaderName")?.asString ?: "Unknown"
                                    val views = obj.get("views")?.asLong ?: 0
                                    val duration = obj.get("duration")?.asLong ?: 0
                                    StreamItem(
                                        url = "/watch?v=$vid",
                                        videoId = vid,
                                        title = title,
                                        thumbnail = thumb,
                                        uploaderName = channel,
                                        uploaderUrl = obj.get("uploaderUrl")?.asString,
                                        uploaderAvatar = obj.get("uploaderAvatar")?.asString,
                                        views = views,
                                        duration = duration,
                                        uploadedDate = obj.get("uploadedDate")?.asString,
                                        uploaded = obj.get("uploaded")?.asLong
                                    )
                                }
                                if (items.isNotEmpty()) {
                                    CrashLogger.d("Piped related from $baseUrl: ${items.size} items")
                                    return@withTimeoutOrNull Result.success(items)
                                }
                            }
                        }
                        CrashLogger.d("Piped related $baseUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Piped related $baseUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(baseUrl)
                    }
                }

                CrashLogger.d("Piped related exhausted, trying Invidious")
                for (instance in getOrderedInvidiousInstances()) {
                    val invidiousUrl = instance.url
                    try {
                        val url = "${invidiousUrl.trimEnd('/')}/api/v1/videos/$videoId?local=true"
                        val startTime = System.currentTimeMillis()
                        val (code, body) = executeRequest(url, shortTimeoutClient)
                        val latency = System.currentTimeMillis() - startTime
                        if (code == 200 && isValidJson(body)) {
                            instanceManager.recordSuccess(invidiousUrl, latency)
                            val parsed = gson.fromJson(body, JsonObject::class.java)
                            val related = parsed?.getAsJsonArray("recommendedVideos")
                            if (related != null && related.size() > 0) {
                                val items = related.mapNotNull { el ->
                                    val obj = el.asJsonObject ?: return@mapNotNull null
                                    val vid = obj.get("videoId")?.asString ?: return@mapNotNull null
                                    val title = obj.get("title")?.asString ?: "Unknown"
                                    val thumb = obj.get("videoThumbnails")?.asJsonArray
                                        ?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                        ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                    val channel = obj.get("author")?.asString ?: "Unknown"
                                    val views = obj.get("viewCount")?.asLong ?: 0
                                    val duration = obj.get("lengthSeconds")?.asLong ?: 0
                                    StreamItem(
                                        url = "/watch?v=$vid",
                                        videoId = vid,
                                        title = title,
                                        thumbnail = thumb,
                                        uploaderName = channel,
                                        uploaderUrl = null,
                                        uploaderAvatar = null,
                                        views = views,
                                        duration = duration,
                                        uploadedDate = null,
                                        uploaded = null
                                    )
                                }
                                if (items.isNotEmpty()) {
                                    CrashLogger.d("Invidious related from $invidiousUrl: ${items.size} items")
                                    return@withTimeoutOrNull Result.success(items)
                                }
                            }
                        }
                        CrashLogger.d("Invidious related $invidiousUrl -> $code")
                    } catch (e: Exception) {
                        CrashLogger.d("Invidious related $invidiousUrl failed: ${e.message}")
                        lastException = e
                        instanceManager.recordFailure(invidiousUrl)
                    }
                }

                Result.failure(lastException ?: Exception("No related videos available"))
            } catch (e: Exception) {
                CrashLogger.e("getRelatedStreams fatal error for $videoId", e)
                Result.failure(Exception("Failed to load related videos: ${e.message}"))
            }
        }
        result ?: Result.failure(Exception("Related streams request timed out for $videoId"))
    }

    /** Recursively collects compact/videoWithContext renderers (ad-filtered) from a /next response. */
    private fun collectCompactVideos(
        element: com.google.gson.JsonElement,
        out: MutableList<StreamItem>
    ) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (isAdItem(obj)) return
                for (key in listOf("compactVideoRenderer", "videoWithContextRenderer")) {
                    val r = obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    if (isAdItem(r) || r.has("promotedContent") || r.has("adSlot")) continue
                    parseCompactVideo(r)?.let(out::add)
                }
                for ((_, v) in obj.entrySet()) collectCompactVideos(v, out)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectCompactVideos(it, out) }
        }
    }

    private fun parseCompactVideo(r: JsonObject): StreamItem? {
        val vid = r.get("videoId")?.asString ?: return null
        val titleObj = r.get("title")?.takeIf { it.isJsonObject }?.asJsonObject
        val title = titleObj?.get("simpleText")?.asString
            ?: titleObj?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString
            ?: "Unknown"
        val channel = r.getAsJsonObject("shortBylineText")?.getAsJsonArray("runs")
            ?.firstOrNull()?.asJsonObject?.get("text")?.asString
            ?: r.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject?.get("text")?.asString
            ?: "Unknown"
        val viewText = r.getAsJsonObject("viewCountText")?.get("simpleText")?.asString ?: ""
        val views = viewText.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
        val lengthText = r.getAsJsonObject("lengthText")?.get("simpleText")?.asString
        val thumb = r.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
            ?.lastOrNull()?.asJsonObject?.get("url")?.asString
            ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
        return StreamItem(
            url = "/watch?v=$vid",
            videoId = vid,
            title = title,
            thumbnail = thumb,
            uploaderName = channel,
            uploaderUrl = null,
            uploaderAvatar = null,
            views = views,
            duration = parseDuration(lengthText),
            uploadedDate = null,
            uploaded = null
        )
    }

    private fun getRelatedFromYouTube(videoId: String): List<StreamItem>? {
        for (client in youtubeClients) {
            val result = getRelatedFromYouTubeWithClient(videoId, client)
            if (result != null) return result
        }
        return null
    }

    private fun getRelatedFromYouTubeWithClient(videoId: String, client: YouTubeClient): List<StreamItem>? {
        try {
            val nextBody = gson.toJson(mapOf(
                "videoId" to videoId,
                "context" to mapOf("client" to client.clientMap)
            ))

            val (code, body) = executePostRequest(
                "https://www.youtube.com/youtubei/v1/next?prettyPrint=false",
                nextBody,
                userAgent = client.userAgent
            )

            if (code != 200 || body == null) {
                CrashLogger.d("YouTube innertube /next ${client.name} returned $code")
                return null
            }

            val json = gson.fromJson(body, JsonObject::class.java) ?: return null
            val results = mutableListOf<StreamItem>()
            // Walk the whole response: compactVideoRenderer items live under different paths per
            // client (twoColumnWatchNextResults.secondaryResults on WEB, singleColumn on mobile).
            collectCompactVideos(json, results)
            CrashLogger.d("YouTube innertube /next ${client.name} for $videoId: ${results.size} related")
            return results.distinctBy { it.videoId }.filter { it.videoId != videoId }.ifEmpty { null }
        } catch (e: Exception) {
            CrashLogger.d("YouTube innertube /next ${client.name} failed: ${e.message}")
            return null
        }
    }

    suspend fun getSuggestions(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val newPipeSuggestions = newPipeSource.getSuggestions(query)
            if (newPipeSuggestions.isNotEmpty()) {
                return@withContext Result.success(newPipeSuggestions)
            }
            for (instance in getOrderedPipedInstances()) {
                val baseUrl = instance.url
                try {
                    val url = "${baseUrl.trimEnd('/')}/suggestions/${java.net.URLEncoder.encode(query, "UTF-8")}"
                    val startTime = System.currentTimeMillis()
                    val (code, body) = executeRequest(url, shortTimeoutClient)
                    val latency = System.currentTimeMillis() - startTime
                    if (code == 200 && isValidJson(body)) {
                        instanceManager.recordSuccess(baseUrl, latency)
                        val items = gson.fromJson(body, Array<String>::class.java)?.toList() ?: emptyList()
                        if (items.isNotEmpty()) return@withContext Result.success(items)
                    }
                } catch (e: Exception) {
                    CrashLogger.d("Piped suggestions $baseUrl failed: ${e.message}")
                    instanceManager.recordFailure(baseUrl)
                }
            }
            CrashLogger.w("All suggestions failed for '$query'")
            Result.success(emptyList())
        } catch (e: Exception) {
            CrashLogger.e("getSuggestions fatal error for '$query'", e)
            Result.success(emptyList())
        }
    }

    private fun searchYouTube(query: String): List<StreamItem>? {
        for (client in youtubeClients) {
            val result = searchYouTubeWithClient(query, client)
            if (result != null) return result
        }
        return null
    }

    private fun searchYouTubeWithClient(query: String, client: YouTubeClient): List<StreamItem>? {
        try {
            val searchBody = gson.toJson(mapOf(
                "query" to query,
                "context" to mapOf("client" to client.clientMap)
            ))

            val (code, body) = executePostRequest(
                "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
                searchBody,
                userAgent = client.userAgent
            )

            if (code != 200 || body == null) {
                CrashLogger.d("YouTube innertube search ${client.name} returned $code")
                return null
            }

            val json = gson.fromJson(body, JsonObject::class.java) ?: return null
            val contents = json.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                ?.getAsJsonObject("primaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return null

            val results = mutableListOf<StreamItem>()
            for (section in contents) {
                val items = section.asJsonObject
                    ?.getAsJsonObject("itemSectionRenderer")
                    ?.getAsJsonArray("contents") ?: continue
                for (item in items) {
                    val obj = item.asJsonObject ?: continue
                    if (obj.has("promotedContent") || obj.has("adSlot") || obj.has("searchAdRenderer")) continue
                    val video = obj.getAsJsonObject("videoRenderer") ?: continue
                    val videoId = video.get("videoId")?.asString ?: continue
                    val title = video.getAsJsonObject("title")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"
                    val channelName = video.getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"
                    val viewCountText = video.getAsJsonObject("viewCountText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString
                    val viewCount = viewCountText?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0
                    val lengthText = video.getAsJsonObject("lengthText")?.get("simpleText")?.asString
                    val duration = parseDuration(lengthText)
                    val thumbnail = video.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                        ?.lastOrNull()?.asJsonObject?.get("url")?.asString
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    results.add(StreamItem(
                        url = "/watch?v=$videoId",
                        videoId = videoId,
                        title = title,
                        thumbnail = thumbnail,
                        uploaderName = channelName,
                        uploaderUrl = null,
                        uploaderAvatar = null,
                        views = viewCount,
                        duration = duration,
                        uploadedDate = null,
                        uploaded = null
                    ))
                }
            }

            CrashLogger.d("YouTube innertube search ${client.name} '$query': ${results.size} results")
            return results.ifEmpty { null }
        } catch (e: Exception) {
            CrashLogger.d("YouTube innertube search ${client.name} failed: ${e.message}")
            return null
        }
    }

    private fun getTrendingFromYouTube(): List<StreamItem>? {
        for (client in youtubeClients) {
            val result = getTrendingFromYouTubeWithClient(client)
            if (result != null) return result
        }
        return null
    }

    private fun getTrendingFromYouTubeWithClient(client: YouTubeClient): List<StreamItem>? {
        try {
            val trendingBody = gson.toJson(mapOf(
                "browseId" to "FEtrending",
                "context" to mapOf("client" to client.clientMap)
            ))

            val (code, body) = executePostRequest(
                "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                trendingBody,
                userAgent = client.userAgent
            )

            if (code != 200 || body == null) {
                CrashLogger.d("YouTube trending ${client.name} returned $code")
                return null
            }

            val json = gson.fromJson(body, JsonObject::class.java) ?: return null
            val contents = json.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")?.firstOrNull()
                ?.asJsonObject?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return null

            val results = mutableListOf<StreamItem>()
            for (section in contents) {
                val items = section.asJsonObject
                    ?.getAsJsonObject("itemSectionRenderer")
                    ?.getAsJsonArray("contents") ?: continue
                for (item in items) {
                    val video = item.asJsonObject?.getAsJsonObject("videoRenderer") ?: continue
                    val videoId = video.get("videoId")?.asString ?: continue
                    val title = video.getAsJsonObject("title")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"
                    val channelName = video.getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown"
                    val viewCountText = video.getAsJsonObject("viewCountText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString
                    val viewCount = viewCountText?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0
                    val lengthText = video.getAsJsonObject("lengthText")?.get("simpleText")?.asString
                    val duration = parseDuration(lengthText)
                    val thumbnail = video.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                        ?.lastOrNull()?.asJsonObject?.get("url")?.asString
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    results.add(StreamItem(
                        url = "/watch?v=$videoId",
                        videoId = videoId,
                        title = title,
                        thumbnail = thumbnail,
                        uploaderName = channelName,
                        uploaderUrl = null,
                        uploaderAvatar = null,
                        views = viewCount,
                        duration = duration,
                        uploadedDate = null,
                        uploaded = null
                    ))
                }
            }

            CrashLogger.d("YouTube trending ${client.name}: ${results.size} results")
            return results.ifEmpty { null }
        } catch (e: Exception) {
            CrashLogger.d("YouTube trending ${client.name} failed: ${e.message}")
            return null
        }
    }

    private fun parseDuration(text: String?): Long? {
        if (text == null) return null
        val parts = text.split(":").map { it.toLongOrNull() ?: 0L }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
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
    thumbnail = "https://i.ytimg.com/vi/${videoId}/maxresdefault.jpg",
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
    subtitles = subtitle?.map { it.toSubtitle() } ?: emptyList(),
    relatedStreams = relatedStreams?.mapNotNull { it.toStreamItem() } ?: emptyList()
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
    videoStreams = ((formatStreams?.map { it.toStreamFormat() } ?: emptyList()) +
            (adaptiveFormats?.map { it.toStreamFormat() } ?: emptyList()))
                .sortedByDescending { (it.height ?: 0) * (it.bitrate ?: 0L) },
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
    bitrate = bitrate?.parseBitrate(),
    width = width,
    height = height,
    fps = fps
)

private fun com.freedomplay.app.data.api.invidious.InvidiousAdaptiveFormat.toStreamFormat() = StreamFormat(
    url = url,
    quality = quality,
    mimeType = type,
    codec = null,
    bitrate = bitrate?.parseBitrate(),
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

private val BITRATE_REGEX = Regex("(\\d+)(k|m|K|M)?", RegexOption.IGNORE_CASE)

private fun String.parseBitrate(): Long? {
    val match = BITRATE_REGEX.find(this) ?: return null
    val value = match.groupValues[1].toLongOrNull() ?: return null
    val unit = match.groupValues[2]
    return when (unit.lowercase()) {
        "k" -> value * 1000
        "m" -> value * 1000_000
        else -> value
    }
}
