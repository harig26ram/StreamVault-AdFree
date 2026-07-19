package com.freedomplay.app.data.repository

import com.freedomplay.app.data.api.invidious.InvidiousApiService
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
    private val invidiousApi: InvidiousApiService,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val instanceManager: InstanceManager,
    private val newPipeSource: NewPipeStreamSource
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
                val url = format.get("url")?.asString
                if (url != null) {
                    videoStreams.add(StreamFormat(
                        url = url,
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
                val url = format.get("url")?.asString
                val mimeType = format.get("mimeType")?.asString ?: ""
                if (url != null) {
                    val streamFormat = StreamFormat(
                        url = url,
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

                CrashLogger.d("Trying YouTube music trending first")
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
        val result = withTimeoutOrNull(30_000L) {
            try {
                var lastException: Exception? = null

                // PRIMARY: NewPipeExtractor (client-side JS sig/nsig deciphering).
                // This is the only source that reliably returns playable high-quality
                // adaptive streams; Piped/Invidious/InnerTube below are fallbacks.
                CrashLogger.d("Trying NewPipeExtractor first for $videoId")
                val newPipeStream = newPipeSource.getStream(videoId)
                if (newPipeStream != null) {
                    val hasPlayable = newPipeStream.videoStreams.any { !it.url.isNullOrBlank() } ||
                        newPipeStream.audioStreams.any { !it.url.isNullOrBlank() }
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
