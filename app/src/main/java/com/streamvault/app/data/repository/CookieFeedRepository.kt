package com.streamvault.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.streamvault.app.auth.CookieStore
import com.streamvault.app.data.api.BrowseRequest
import com.streamvault.app.data.api.ClientContext
import com.streamvault.app.data.api.ClientInfo
import com.streamvault.app.data.api.YouTubeApiService
import com.streamvault.app.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface CookieFeedRepository {
    suspend fun getPersonalizedHomeFeed(): List<Video>
}

@Singleton
class CookieFeedRepositoryImpl @Inject constructor(
    private val apiService: YouTubeApiService,
    private val cookieStore: CookieStore
) : CookieFeedRepository {

    companion object {
        private const val TAG = "CookieFeedRepo"

        fun computeSapiSidHash(sapisid: String, origin: String = "https://www.youtube.com"): String {
            val timestamp = System.currentTimeMillis() / 1000
            val input = "$timestamp ${sapisid} $origin"
            val digest = MessageDigest.getInstance("SHA-1")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
            return "SAPISIDHASH ${timestamp}_$hashHex"
        }

        fun extractSapiSid(cookies: String): String? {
            return cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("SAPISID=", ignoreCase = true) }
                ?.substringAfter("=")
                ?.takeIf { it.isNotEmpty() }
        }

        fun parsePersonalizedFeed(responseBody: String): List<Video> {
            val items = mutableListOf<Video>()
            try {
                val gson = Gson()
                val root = gson.fromJson(responseBody, JsonObject::class.java) ?: return items

                // Try singleColumnBrowseResultsRenderer path
                val singleCol = root.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                if (singleCol != null) {
                    singleCol.getAsJsonArray("tabs")?.forEach { tab ->
                        val sections = tab.asJsonObject
                            ?.getAsJsonObject("tabRenderer")
                            ?.getAsJsonObject("content")
                            ?.getAsJsonObject("sectionListRenderer")
                            ?.getAsJsonArray("contents")
                        sections?.forEach { section ->
                            val secObj = section.asJsonObject
                            secObj.getAsJsonObject("itemSectionRenderer")
                                ?.getAsJsonArray("contents")?.forEach { item ->
                                    extractVideosRecursive(item.asJsonObject, items)
                                }
                            secObj.getAsJsonObject("richGridRenderer")
                                ?.getAsJsonArray("contents")?.forEach { item ->
                                    extractVideosRecursive(item.asJsonObject, items)
                                }
                        }
                    }
                }

                // Try twoColumnBrowseResultsRenderer path
                val twoCol = root.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                if (twoCol != null) {
                    twoCol.getAsJsonArray("tabs")?.forEach { tab ->
                        val tabRenderer = tab.asJsonObject?.getAsJsonObject("tabRenderer")
                        val content = tabRenderer?.getAsJsonObject("content")

                        // sectionListRenderer path
                        val sectionContents = content
                            ?.getAsJsonObject("sectionListRenderer")
                            ?.getAsJsonArray("contents")
                        sectionContents?.forEach { section ->
                            val secObj = section.asJsonObject
                            secObj.getAsJsonObject("itemSectionRenderer")
                                ?.getAsJsonArray("contents")?.forEach { item ->
                                    extractVideosRecursive(item.asJsonObject, items)
                                }
                            secObj.getAsJsonObject("richGridRenderer")
                                ?.getAsJsonArray("contents")?.forEach { item ->
                                    extractVideosRecursive(item.asJsonObject, items)
                                }
                        }

                        // richGridRenderer at tab content level
                        val richGrid = content?.getAsJsonObject("richGridRenderer")
                        richGrid?.getAsJsonArray("contents")?.forEach { item ->
                            extractVideosRecursive(item.asJsonObject, items)
                        }
                    }
                }

                // Fallback: recursive search for video renderers
                if (items.isEmpty()) {
                    recursiveVideoSearch(root, items)
                }
            } catch (_: Exception) {
                // Parsing failed; return whatever items were extracted
            }
            return items
        }

        private fun extractVideosRecursive(obj: JsonObject?, items: MutableList<Video>) {
            if (obj == null) return

            // Direct videoRenderer
            obj.getAsJsonObject("videoRenderer")?.let { renderer ->
                jsonToVideo(renderer)?.let { items.add(it) }
            }

            // Compact video
            obj.getAsJsonObject("compactVideoRenderer")?.let { renderer ->
                jsonToVideo(renderer)?.let { items.add(it) }
            }

            // Grid video
            obj.getAsJsonObject("gridVideoRenderer")?.let { renderer ->
                jsonToVideo(renderer)?.let { items.add(it) }
            }

            // Rich item containing video
            obj.getAsJsonObject("richItemRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("videoRenderer")?.let { renderer ->
                    jsonToVideo(renderer)?.let { items.add(it) }
                }

            // Rich section renderer (YouTube 2024+ format)
            obj.getAsJsonObject("richSectionRenderer")?.let { rs ->
                rs.getAsJsonObject("content")?.let { extractVideosRecursive(it, items) }
                rs.getAsJsonArray("contents")?.forEach { child ->
                    extractVideosRecursive(child.asJsonObject, items)
                }
            }

            // Shelf with expanded contents
            obj.getAsJsonObject("shelfRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("expandedShelfContentsRenderer")
                ?.getAsJsonArray("items")?.forEach { shelfItem ->
                    extractVideosRecursive(shelfItem.asJsonObject, items)
                }

            // Element renderer fallback — regex extract videoIds
            obj.getAsJsonObject("elementRenderer")?.let { elem ->
                extractFromElementRenderer(elem, items)
            }
        }

        private fun extractFromElementRenderer(elem: JsonObject, items: MutableList<Video>) {
            val elemStr = elem.toString()
            val vidIdPattern = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
            val titlePattern = Regex("\"title\":\\{\"simpleText\":\"([^\"]+)\"\\}")
            val runsTitlePattern = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"")
            val channelPattern = Regex("\"shortBylineText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"")
            val lengthPattern = Regex("\"lengthText\":\\{\"accessibility\":\\{\"accessibilityData\":\\{\"label\":\"([^\"]+)\"")
            val viewPattern = Regex("\"viewCountText\":\\{\"simpleText\":\"([^\"]+)\"")
            val publishedPattern = Regex("\"publishedTimeText\":\\{\"simpleText\":\"([^\"]+)\"")
            val thumbPattern = Regex("\"thumbnails\":\\[\\{\"url\":\"([^\"]+)\"")

            val videoIds = vidIdPattern.findAll(elemStr).map { it.groupValues[1] }.distinct().toList()
            val titles = titlePattern.findAll(elemStr).map { it.groupValues[1].replace("\\u0026", "&") }.toList()
            val runsTitles = runsTitlePattern.findAll(elemStr).map { it.groupValues[1].replace("\\u0026", "&") }.toList()
            val channels = channelPattern.findAll(elemStr).map { it.groupValues[1] }.toList()
            val lengths = lengthPattern.findAll(elemStr).map { it.groupValues[1] }.toList()
            val views = viewPattern.findAll(elemStr).map { it.groupValues[1] }.toList()
            val published = publishedPattern.findAll(elemStr).map { it.groupValues[1] }.toList()
            val thumbs = thumbPattern.findAll(elemStr).map { it.groupValues[1] }.toList()

            videoIds.forEachIndexed { i, vidId ->
                if (vidId.length == 11) {
                    val allTitles = titles + runsTitles
                    items.add(Video(
                        id = vidId,
                        title = allTitles.getOrNull(i) ?: "",
                        channelName = channels.getOrNull(i) ?: "",
                        channelId = "",
                        channelAvatar = "",
                        thumbnailUrl = thumbs.getOrNull(i) ?: "",
                        duration = lengths.getOrNull(i) ?: "",
                        viewCount = views.getOrNull(i) ?: "",
                        publishedTime = published.getOrNull(i) ?: "",
                        description = ""
                    ))
                }
            }
        }

        private fun recursiveVideoSearch(obj: JsonObject?, items: MutableList<Video>) {
            if (obj == null) return
            extractVideosRecursive(obj, items)
            obj.entrySet().forEach { entry ->
                val value = entry.value
                if (value.isJsonObject) {
                    recursiveVideoSearch(value.asJsonObject, items)
                } else if (value.isJsonArray) {
                    value.asJsonArray.forEach { element ->
                        if (element.isJsonObject) {
                            recursiveVideoSearch(element.asJsonObject, items)
                        }
                    }
                }
            }
        }

        fun jsonToVideo(renderer: JsonObject): Video? {
            val videoId = renderer.get("videoId")?.asString ?: return null
            val title = renderer.getAsJsonObject("title")?.get("simpleText")?.asString
                ?: renderer.getAsJsonObject("title")?.getAsJsonArray("runs")
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                ?: ""
            val channel = renderer.getAsJsonObject("shortBylineText")
                ?.getAsJsonArray("runs")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString
                ?: renderer.getAsJsonObject("ownerText")
                ?.getAsJsonArray("runs")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString
                ?: ""
            val thumb = renderer.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")?.lastOrNull()
                ?.asJsonObject?.get("url")?.asString ?: ""
            val duration = renderer.getAsJsonObject("lengthText")
                ?.getAsJsonObject("accessibility")
                ?.getAsJsonObject("accessibilityData")
                ?.get("label")?.asString ?: ""
            val views = renderer.getAsJsonObject("viewCountText")
                ?.get("simpleText")?.asString ?: ""
            val published = renderer.getAsJsonObject("publishedTimeText")
                ?.get("simpleText")?.asString ?: ""

            return Video(
                id = videoId,
                title = title,
                channelName = channel,
                channelId = "",
                channelAvatar = "",
                thumbnailUrl = thumb,
                duration = duration,
                viewCount = views,
                publishedTime = published,
                description = ""
            )
        }
    }

    override suspend fun getPersonalizedHomeFeed(): List<Video> = withContext(Dispatchers.IO) {
        try {
            val cookies = cookieStore.getCookies()
            val sapisid = cookieStore.getSapisid()

            if (cookies.isNullOrEmpty() || sapisid.isNullOrEmpty()) {
                Log.w(TAG, "No cookies or SAPISID available")
                return@withContext emptyList()
            }

            val cookieHash = computeSapiSidHash(sapisid)
            Log.d(TAG, "Fetching personalized feed with SAPISIDHASH")

            // Build the request — header injection (Cookie + SAPISIDHASH) will be
            // handled by NetworkModule's OkHttp interceptor in T5.
            // For now, the Retrofit call goes through the default interceptor.
            val ctx = ClientContext(
                client = ClientInfo(
                    clientName = "WEB",
                    clientVersion = "2.20260623.01.00",
                    hl = "en",
                    gl = "US"
                )
            )
            val request = BrowseRequest(context = ctx, browseId = "FEwhat_to_watch")
            val response = apiService.browseRaw(request)

            if (response.isSuccessful) {
                val rawBody = response.body()?.string() ?: ""
                Log.d(TAG, "Personalized feed response length: ${rawBody.length}")
                val videos = parsePersonalizedFeed(rawBody)
                Log.d(TAG, "Parsed ${videos.size} personalized videos")
                videos
            } else {
                Log.w(TAG, "Personalized feed error: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Personalized feed exception: ${e.message}")
            emptyList()
        }
    }
}
