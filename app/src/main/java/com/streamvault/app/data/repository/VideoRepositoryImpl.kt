package com.streamvault.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.streamvault.app.data.api.BrowseRequest
import com.streamvault.app.data.api.ClientContext
import com.streamvault.app.data.api.ClientInfo
import com.streamvault.app.data.api.ThirdPartyContext
import com.streamvault.app.data.api.NextRequest
import com.streamvault.app.data.api.PlayerRequest
import com.streamvault.app.data.api.SearchRequest
import com.streamvault.app.data.api.PlaybackContext
import com.streamvault.app.data.api.ContentPlaybackContext
import com.streamvault.app.data.api.YouTubeApiService
import com.streamvault.app.data.api.VideoDetails
import com.streamvault.app.auth.CookieStore
import com.streamvault.app.data.bootstrap.VisitorDataBootstrapper
import com.streamvault.app.data.local.VideoDao
import com.streamvault.player.youtube.CipherDecryptor
import com.streamvault.player.youtube.NParamDecryptor
import com.streamvault.player.youtube.PlayerJsFetcher
import com.streamvault.player.youtube.StreamUrlExtractor
import com.streamvault.app.data.model.ExpandedShelfContentsRenderer
import com.streamvault.app.data.model.GridRenderer
import com.streamvault.app.data.model.GridVideoRenderer
import com.streamvault.app.data.model.HorizontalListRenderer
import com.streamvault.app.data.model.ItemContent
import com.streamvault.app.data.model.PlaylistRenderer
import com.streamvault.app.data.model.SectionContent
import com.streamvault.app.data.model.ShelfRenderer
import com.streamvault.app.data.model.Text as ModelText
import com.streamvault.app.data.model.VideoRenderer
import com.streamvault.app.data.model.ChannelRenderer
import com.streamvault.app.data.model.CompactVideoRenderer
import com.streamvault.app.data.model.RadioRenderer
import com.streamvault.app.domain.model.Channel
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.HomeFeed
import com.streamvault.app.domain.model.SearchResult
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.repository.VideoRepository
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val apiService: YouTubeApiService,
    private val videoDao: VideoDao,
    @javax.inject.Named("general") private val httpClient: okhttp3.OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val visitorDataBootstrapper: VisitorDataBootstrapper,
    private val cookieFeedRepository: CookieFeedRepository,
    private val cookieStore: CookieStore
) : VideoRepository {

    private val streamUrlExtractor = StreamUrlExtractor(
        cipherDecryptor = CipherDecryptor(),
        nParamDecryptor = NParamDecryptor()
    )
    private val playerJsFetcher = PlayerJsFetcher()

    private data class BrowseParseResult(
        val items: List<FeedItem>,
        val continuationToken: String?
    )

    companion object {
        private const val TAG = "VideoRepository"
    }

    private var lastGoodFeed: List<FeedItem>? = null

    override suspend fun getHomeFeed(continuationToken: String?): Result<HomeFeed> {
        if (continuationToken != null) {
            return loadHomeFeedContinuation(continuationToken)
        }
        return try {
            Log.d(TAG, "Home feed request")

            // Tier 0: Cookie-ML personalized feed (highest quality when connected)
            if (cookieStore.isConnected.value) {
                try {
                    val personalizedItems = cookieFeedRepository.getPersonalizedHomeFeed()
                    if (personalizedItems.isNotEmpty()) {
                        Log.d(TAG, "Cookie-ML feed returned ${personalizedItems.size} items")
                        val feedItems = personalizedItems.map { FeedItem.Video(it) }
                        lastGoodFeed = feedItems
                        return Result.success(HomeFeed(items = feedItems, continuationToken = null, fromCookieFeed = true))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cookie-ML feed failed: ${e.message}, continuing to fallback")
                }
            }

            Log.d(TAG, "Trying FEwhat_to_watch InnerTube browse")
            val browseResult = fetchBrowseFeed("FEwhat_to_watch")
            if (browseResult.items.isNotEmpty()) {
                Log.d(TAG, "FEwhat_to_watch returned ${browseResult.items.size} items")
                return Result.success(HomeFeed(items = browseResult.items, continuationToken = browseResult.continuationToken))
            }

            Log.d(TAG, "FEwhat_to_watch empty, trying homepage HTML scrape")
            val htmlItems = fetchHomePageFeed()
            if (htmlItems.isNotEmpty()) {
                return Result.success(HomeFeed(items = htmlItems, continuationToken = null))
            }

            Log.d(TAG, "HTML scrape empty, using search API as home feed source with watch history")
            val (searchItems, searchContinuation) = fetchSearchBasedHomeFeed()
            if (searchItems.isNotEmpty()) {
                return Result.success(HomeFeed(items = searchItems, continuationToken = searchContinuation))
            }

            Log.d(TAG, "Search feed empty, falling back to trending")
            val trendingResult = getTrending()
            if (trendingResult.isSuccess) {
                val trending = trendingResult.getOrNull()
                if (trending != null && trending.items.isNotEmpty()) {
                    Log.d(TAG, "Trending fallback returned ${trending.items.size} items")
                    return Result.success(trending)
                }
            }

            // Final fallback: return cached feed if available
            val cached = lastGoodFeed
            if (!cached.isNullOrEmpty()) {
                Log.d(TAG, "Returning cached feed (${cached.size} items)")
                return Result.success(HomeFeed(items = cached, continuationToken = null))
            }

            Log.w(TAG, "All feed sources exhausted (browse, HTML, search, trending)")
            Result.failure(Exception("No content available. Please check your connection and try again."))
        } catch (e: Exception) {
            Log.w(TAG, "Home feed exception: ${e.message}")
            Result.failure(e)
        }
    }

private suspend fun loadHomeFeedContinuation(continuationToken: String): Result<HomeFeed> {
        return try {
            var ctx = webContext()
            // Get visitorData from bootstrapper (cached or fresh)
            val vd = visitorDataBootstrapper.getCachedVisitorData()
            vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }
            val request = BrowseRequest(context = ctx, browseId = "FEwhat_to_watch", params = continuationToken)
            val rawResponse = apiService.browseRaw(request)
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                val result = parseBrowseResponse(rawBody)
                Result.success(HomeFeed(items = result.items, continuationToken = result.continuationToken))
            } else {
                Result.failure(Exception("Continuation error: ${rawResponse.code()}"))
            }
} catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchHomePageFeed(): List<FeedItem> = withContext(Dispatchers.IO) {
        // Ensure bootstrapper has fresh data
        visitorDataBootstrapper.ensureBootstrapped()
        
        val chromeUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        try {
            Log.d(TAG, "Fetching YouTube homepage for initial data...")
            val homepageReq = okhttp3.Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", chromeUA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val homepageResp = httpClient.newCall(homepageReq).execute()
            val homepageHtml = homepageResp.body?.string() ?: ""
            Log.d(TAG, "Homepage HTML length: ${homepageHtml.length}")

            if (homepageHtml.isNotEmpty()) {
                // Extraction now done by bootstrapper
                visitorDataBootstrapper.ensureBootstrapped()
            }

            var items = extractVideosFromHomepageHtml(homepageHtml)
            Log.d(TAG, "Homepage: extracted ${items.size} items")

            if (items.isEmpty()) {
                Log.d(TAG, "Homepage empty, fetching trending page...")
                val trendingReq = okhttp3.Request.Builder()
                    .url("https://www.youtube.com/feed/trending")
                    .header("User-Agent", chromeUA)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
                val trendingResp = httpClient.newCall(trendingReq).execute()
                val trendingHtml = trendingResp.body?.string() ?: ""
                Log.d(TAG, "Trending HTML length: ${trendingHtml.length}")
                items = extractVideosFromHomepageHtml(trendingHtml)
                Log.d(TAG, "Trending: extracted ${items.size} items")
            }

            items
        } catch (e: Exception) {
            Log.w(TAG, "Homepage fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun getSearchHistoryTopics(): List<String> {
        return try {
            val prefs = context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("search_history", null) ?: return emptyList()
            val array = JSONArray(json)
            val queries = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val q = array.getString(i).trim()
                if (q.isNotEmpty() && queries.none { it.equals(q, ignoreCase = true) }) {
                    queries.add(q)
                }
            }
            queries.takeLast(5).reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getWatchHistoryTopics(): List<String> = withContext(Dispatchers.IO) {
        try {
            val recentVideos = videoDao.getWatchHistorySync().take(20)
            val channelIds = recentVideos.mapNotNull { it.channelId }.distinct().take(5)
            val topics = mutableListOf<String>()

            for (channelId in channelIds) {
                topics.add("channel:$channelId")
            }

            val keywords = recentVideos.mapNotNull { video ->
                val title = video.title
                val words = title.split("\\s+".toRegex())
                    .filter { it.length > 3 && it.lowercase() !in setOf("the", "this", "that", "with", "from", "have", "been", "were", "will", "would", "could", "should", "about", "into", "just", "like", "more", "some", "than", "them", "then", "your", "what", "when", "which", "there", "their", "other") }
                words.take(3).joinToString(" ")
            }.filter { it.length > 3 }.distinct().take(3)

            topics.addAll(keywords)

            if (topics.isEmpty()) {
                Log.d(TAG, "No watch history topics available")
                return@withContext emptyList()
            }

            Log.d(TAG, "Watch history topics: $topics (${recentVideos.size} recent videos)")
            topics
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get watch history topics: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchSearchBasedHomeFeed(): Pair<List<FeedItem>, String?> = withContext(Dispatchers.IO) {
        val searchHistoryTopics = getSearchHistoryTopics()
        val watchHistoryTopics = getWatchHistoryTopics()
        val fallbackTopics = listOf("trending", "popular music", "viral videos", "news today", "tech")

        val topics = if (searchHistoryTopics.isNotEmpty() && watchHistoryTopics.isNotEmpty()) {
            val interleaved = mutableListOf<String>()
            val maxSize = maxOf(searchHistoryTopics.size, watchHistoryTopics.size)
            for (i in 0 until maxSize) {
                if (i < searchHistoryTopics.size) interleaved.add(searchHistoryTopics[i])
                if (i < watchHistoryTopics.size) interleaved.add(watchHistoryTopics[i])
            }
            interleaved.take(8)
        } else if (searchHistoryTopics.isNotEmpty()) {
            searchHistoryTopics
        } else if (watchHistoryTopics.isNotEmpty()) {
            watchHistoryTopics
        } else {
            fallbackTopics
        }

        Log.d(TAG, "Search-based feed topics: $topics (search: ${searchHistoryTopics.size}, watch: ${watchHistoryTopics.size})")
        val allItems = mutableListOf<FeedItem>()
        var lastContinuationToken: String? = null
        try {
            for (topic in topics) {
                var ctx = webContext()
                val vd = visitorDataBootstrapper.getCachedVisitorData()
                vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }

                val request = if (topic.startsWith("channel:")) {
                    val channelId = topic.removePrefix("channel:")
                    SearchRequest(context = ctx, query = "", params = buildChannelParams(channelId))
                } else {
                    SearchRequest(context = ctx, query = topic)
                }

                val rawResponse = apiService.searchRaw(request)
                if (rawResponse.isSuccessful) {
                    val rawBody = rawResponse.body()?.string() ?: ""
                    val parsed = parseSearchResponse(rawBody)
                    for (item in parsed.items) {
                        if (allItems.none { existing ->
                            existing is FeedItem.Video && item is FeedItem.Video && existing.video.id == item.video.id
                        }) {
                            allItems.add(item)
                        }
                    }
                    if (parsed.continuationToken != null && lastContinuationToken == null) {
                        lastContinuationToken = parsed.continuationToken
                    }
                }
                if (allItems.size >= 25) break
            }
            Log.d(TAG, "Search-based home feed: ${allItems.size} unique videos, continuation=${lastContinuationToken != null}")
            Pair(allItems, lastContinuationToken)
        } catch (e: Exception) {
            Log.w(TAG, "Search-based home feed failed: ${e.message}")
            Pair(emptyList(), null)
        }
    }

    private fun buildChannelParams(channelId: String): String {
        return try {
            val json = org.json.JSONObject().apply {
                put("0:0:2", org.json.JSONObject().apply {
                    put("1:0:2", org.json.JSONObject().apply {
                        put("1:0:2", channelId)
                    })
                })
            }
            val encoded = android.util.Base64.encodeToString(
                json.toString().toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            )
            encoded
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractVisitorDataFromHtml(html: String) {
        try {
            val pattern = Regex("\"visitorData\":\"([^\"]+)\"")
            val match = pattern.find(html)
            if (match != null) {
                val vd = match.groupValues[1]
                Log.d(TAG, "Extracted visitorData from HTML: ${vd.take(50)}...")
            }
        } catch (_: Exception) {}
    }

    private fun extractApiKeyFromHtml(html: String) {
        try {
            val pattern = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
            val match = pattern.find(html)
            if (match != null) {
                val key = match.groupValues[1]
                Log.d(TAG, "Extracted API key from HTML: $key")
            }
        } catch (_: Exception) {}
    }

    private fun extractVideosFromHomepageHtml(html: String): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        
        // Try multiple regex patterns for ytInitialData (YouTube sometimes has multiple)
        val ytInitialDataPatterns = listOf(
            Regex("(?s)var ytInitialData = (\\{.*?\\});</script>"),
            Regex("(?s)window\\[\"ytInitialData\"\\] = (\\{.*?\\});"),
            Regex("(?s)ytInitialData\\s*=\\s*(\\{.*?\\});"),
        )
        
        var root: JsonObject? = null
        var matchedPattern = -1
        
        for ((index, pattern) in ytInitialDataPatterns.withIndex()) {
            val match = pattern.find(html)
            if (match != null) {
                val jsonStr = match.groupValues[1]
                Log.d(TAG, "ytInitialData found via pattern $index, length: ${jsonStr.length}")
                try {
                    root = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    matchedPattern = index
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse ytInitialData from pattern $index: ${e.message}")
                }
            }
        }
        
        if (root == null) {
            Log.d(TAG, "No ytInitialData found in HTML with any pattern")
            return items
        }
        
        Log.d(TAG, "Parsed ytInitialData root with pattern $matchedPattern")
        
        // Try multiple extraction paths in order of likelihood
        val extractionPaths = listOf(
            // Path 1: Homepage richGridRenderer (primary)
            { root: JsonObject? ->
                root?.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("richGridRenderer")
                    ?.getAsJsonArray("contents")
            },
            // Path 2: Trending page sectionListRenderer
            { root: JsonObject? ->
                root?.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents")
            },
            // Path 3: Search results twoColumnSearchResultsRenderer
            { root: JsonObject? ->
                root?.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                    ?.getAsJsonArray("primaryContents")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents")
            },
            // Path 4: Browse feed (channel/playlist) richGridRenderer
            { root: JsonObject? ->
                root?.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")
                    ?.asSequence()
                    ?.mapNotNull { it.asJsonObject?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")?.getAsJsonObject("richGridRenderer")?.getAsJsonArray("contents") }
                    ?.firstOrNull()
            },
            // Path 5: Generic richGridRenderer anywhere in the tree
            { root: JsonObject? ->
                findRichGridRendererRecursive(root)
            },
            // Path 6: Generic shelfRenderer anywhere
            { root: JsonObject? ->
                findShelfRendererRecursive(root)
            }
        )
        
        for ((pathIndex, pathFn) in extractionPaths.withIndex()) {
            if (items.isNotEmpty()) break
            try {
                val contents = pathFn(root)
                if (contents != null && contents.size() > 0) {
                    Log.d(TAG, "Extraction path $pathIndex succeeded with ${contents.size()} items")
                    contents.forEach { item ->
                        if (item.isJsonObject) {
                            val itemObj = item.asJsonObject
                            if (itemObj.has("richSectionRenderer")) {
                                val rsContent = itemObj.getAsJsonObject("richSectionRenderer")
                                    ?.getAsJsonObject("content")
                                rsContent?.let { extractVideosFromJson(it, items) }
                            } else {
                                extractVideosFromJson(itemObj, items)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Extraction path $pathIndex failed: ${e.message}")
            }
        }
        
        // Fallback: recursively search entire JSON tree for video renderers
        if (items.isEmpty()) {
            Log.d(TAG, "All structured paths failed, falling back to recursive search")
            recursiveVideoSearch(root, items)
        }
        
        Log.d(TAG, "Total extracted items: ${items.size}")
        return items
    }
    
    private fun findRichGridRendererRecursive(obj: JsonObject?): JsonArray? {
        if (obj == null) return null
        if (obj.has("richGridRenderer")) {
            return obj.getAsJsonObject("richGridRenderer").getAsJsonArray("contents")
        }
        for (entry in obj.asJsonObject.entrySet()) {
            val value = entry.value
            if (value.isJsonObject) {
                val result = findRichGridRendererRecursive(value.asJsonObject)
                if (result != null) return result
            } else if (value.isJsonArray) {
                for (element in value.asJsonArray) {
                    if (element.isJsonObject) {
                        val result = findRichGridRendererRecursive(element.asJsonObject)
                        if (result != null) return result
                    }
                }
            }
        }
        return null
    }
    
    private fun findShelfRendererRecursive(obj: JsonObject?): JsonArray? {
        if (obj == null) return null
        if (obj.has("shelfRenderer")) {
            val shelf = obj.getAsJsonObject("shelfRenderer")
            return shelf.getAsJsonObject("content")
                ?.getAsJsonObject("expandedShelfContentsRenderer")
                ?.getAsJsonArray("items")
        }
        for (entry in obj.asJsonObject.entrySet()) {
            val value = entry.value
            if (value.isJsonObject) {
                val result = findShelfRendererRecursive(value.asJsonObject)
                if (result != null) return result
            } else if (value.isJsonArray) {
                for (element in value.asJsonArray) {
                    if (element.isJsonObject) {
                        val result = findShelfRendererRecursive(element.asJsonObject)
                        if (result != null) return result
                    }
                }
            }
        }
        return null
    }
    
    private fun recursiveVideoSearch(obj: JsonObject?, items: MutableList<FeedItem>) {
        if (obj == null) return
        
        // Check for direct video renderers
        extractVideosFromJson(obj, items)
        
        // Recurse into all objects and arrays
        obj.asJsonObject.entrySet().forEach { entry ->
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

    private suspend fun fetchBrowseFeed(browseId: String): BrowseParseResult = withContext(Dispatchers.IO) {
        try {
            var ctx = webContext()
            val vd = visitorDataBootstrapper.getCachedVisitorData()
            vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }
            val request = BrowseRequest(context = ctx, browseId = browseId)
            val rawResponse = apiService.browseRaw(request)
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                Log.d(TAG, "$browseId response length: ${rawBody.length}")
                parseBrowseResponse(rawBody)
            } else {
                Log.w(TAG, "$browseId error: ${rawResponse.code()}")
                BrowseParseResult(emptyList(), null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "$browseId exception: ${e.message}")
            BrowseParseResult(emptyList(), null)
        }
    }

    private fun extractContinuationToken(root: JsonObject): String? {
        try {
            val actions = root.getAsJsonArray("onResponseReceivedActions") ?: return null
            for (action in actions) {
                val appendAction = action.asJsonObject
                    ?.getAsJsonObject("appendContinuationItemsAction") ?: continue
                val continuationItems = appendAction.getAsJsonArray("continuationItems") ?: continue
                for (item in continuationItems) {
                    val token = item.asJsonObject
                        ?.getAsJsonObject("continuationEndpoint")
                        ?.getAsJsonObject("continuationCommand")
                        ?.get("token")?.asString
                    if (token != null) return token
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractContinuationToken error: ${e.message}")
        }
        return null
    }

    private fun parseBrowseResponse(rawBody: String): BrowseParseResult {
        val items = mutableListOf<FeedItem>()
        var continuationToken: String? = null
        try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(rawBody, com.google.gson.JsonObject::class.java)
            Log.d(TAG, "parseBrowse: root keys=${root?.keySet()}")

            // Try singleColumnBrowseResultsRenderer
            val singleCol = root?.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
            if (singleCol != null) {
                Log.d(TAG, "parseBrowse: found singleColumn")
                val tabs = singleCol.getAsJsonArray("tabs")
                Log.d(TAG, "parseBrowse: tabs=${tabs?.size()}")
                tabs?.forEach { tab ->
                    val sections = tab.asJsonObject
                        ?.getAsJsonObject("tabRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents")
                    Log.d(TAG, "parseBrowse: sections=${sections?.size()}")
                    sections?.forEach { section ->
                        val secObj = section.asJsonObject
                        Log.d(TAG, "parseBrowse: section keys=${secObj.keySet()}")
                        secObj.getAsJsonObject("itemSectionRenderer")
                            ?.getAsJsonArray("contents")?.forEach { item ->
                                extractVideosFromJson(item.asJsonObject, items)
                            }
                        secObj.getAsJsonObject("richGridRenderer")
                            ?.getAsJsonArray("contents")?.forEach { item ->
                                extractVideosFromJson(item.asJsonObject, items)
                            }
                    }
                }
            }

            // Try twoColumnBrowseResultsRenderer
            val twoCol = root?.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
            if (twoCol != null) {
                Log.d(TAG, "parseBrowse: found twoColumn, keys=${twoCol.keySet()}")
                val tabs = twoCol.getAsJsonArray("tabs")
                Log.d(TAG, "parseBrowse: twoCol tabs=${tabs?.size()}")
                tabs?.forEachIndexed { i, tab ->
                    val tabObj = tab.asJsonObject
                    val tabRenderer = tabObj?.getAsJsonObject("tabRenderer")
                    val tabId = tabRenderer?.get("tabId")?.asString
                    val content = tabRenderer?.getAsJsonObject("content")
                    Log.d(TAG, "  tab[$i] id=$tabId, content keys=${content?.keySet()}")
                    val sectionList = content?.getAsJsonObject("sectionListRenderer")
                    val sectionContents = sectionList?.getAsJsonArray("contents")
                    Log.d(TAG, "  tab[$i] sections=${sectionContents?.size()}")
                    sectionContents?.forEach { section ->
                        val secObj = section.asJsonObject
                        Log.d(TAG, "    section keys=${secObj.keySet()}")
                        secObj.getAsJsonObject("itemSectionRenderer")
                            ?.getAsJsonArray("contents")?.forEach { item ->
                                extractVideosFromJson(item.asJsonObject, items)
                            }
                        secObj.getAsJsonObject("richGridRenderer")
                            ?.getAsJsonArray("contents")?.forEach { item ->
                                extractVideosFromJson(item.asJsonObject, items)
                            }
                    }
                    // Also try content.richGridRenderer
                    val richGrid = content?.getAsJsonObject("richGridRenderer")
                    if (richGrid != null) {
                        val richContents = richGrid.getAsJsonArray("contents")
                        Log.d(TAG, "  tab[$i] richGrid contents=${richContents?.size()}")
                        richContents?.forEach { item ->
                            val itemObj = item.asJsonObject
                            Log.d(TAG, "    richItem keys: ${itemObj.keySet()}")
                            val ri = itemObj.getAsJsonObject("richItemRenderer")
                            if (ri != null) {
                                val riContent = ri.getAsJsonObject("content")
                                Log.d(TAG, "      richItem content keys: ${riContent?.keySet()}")
                                val vr = riContent?.getAsJsonObject("videoRenderer")
                                if (vr != null) {
                                    Log.d(TAG, "      VIDEO: ${vr.get("videoId")}")
                                }
                            }
                            extractVideosFromJson(itemObj, items)
                        }
                    }
                }
            }

            if (singleCol == null && twoCol == null) {
                Log.d(TAG, "parseBrowse: no known renderer found")
                Log.d(TAG, "parseBrowse: contents=${root?.getAsJsonObject("contents")?.keySet()}")
            }
            if (root != null) {
                continuationToken = extractContinuationToken(root)
                if (continuationToken != null) {
                    Log.d(TAG, "parseBrowse: found continuation token from onResponseReceivedActions")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseBrowseResponse error: ${e.message}")
        }
        Log.d(TAG, "parseBrowse: total items=${items.size}, continuationToken=${continuationToken != null}")
        return BrowseParseResult(items, continuationToken)
    }

    private fun parseSearchResponse(rawBody: String): BrowseParseResult {
        val items = mutableListOf<FeedItem>()
        var continuationToken: String? = null
        try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(rawBody, com.google.gson.JsonObject::class.java)
            Log.d(TAG, "Search: root keys=${root?.keySet()}")

            // Search uses twoColumnSearchResultsRenderer
            val searchResults = root?.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
            if (searchResults != null) {
                Log.d(TAG, "Search: found twoColumnSearchResultsRenderer, keys=${searchResults.keySet()}")
                val primaryContent = searchResults.getAsJsonObject("primaryContents")
                Log.d(TAG, "Search: primaryContent keys=${primaryContent?.keySet()}")
                val sectionList = primaryContent?.getAsJsonObject("sectionListRenderer")
                val sections = sectionList?.getAsJsonArray("contents")
                Log.d(TAG, "Search: sections=${sections?.size()}")
                sections?.forEach { section ->
                    val secObj = section.asJsonObject
                    Log.d(TAG, "  section keys=${secObj.keySet()}")
                    secObj.getAsJsonObject("itemSectionRenderer")
                        ?.getAsJsonArray("contents")?.forEach { item ->
                            val itemObj = item.asJsonObject
                            Log.d(TAG, "    item keys: ${itemObj.keySet()}")
                            extractVideosFromJson(itemObj, items)
                        }
                }
            }

            // Fallback: try twoColumnBrowseResultsRenderer
            if (searchResults == null) {
                val twoCol = root?.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                if (twoCol != null) {
                    val tabs = twoCol.getAsJsonArray("tabs")
                    tabs?.forEach { tab ->
                        val sections = tab.asJsonObject
                            ?.getAsJsonObject("tabRenderer")
                            ?.getAsJsonObject("content")
                            ?.getAsJsonObject("sectionListRenderer")
                            ?.getAsJsonArray("contents")
                        sections?.forEach { section ->
                            val secObj = section.asJsonObject
                            secObj.getAsJsonObject("itemSectionRenderer")
                                ?.getAsJsonArray("contents")?.forEach { item ->
                                    extractVideosFromJson(item.asJsonObject, items)
                                }
                        }
                    }
                }
            }

            if (root != null) {
                continuationToken = extractContinuationToken(root)
                if (continuationToken != null) {
                    Log.d(TAG, "Search: found continuation token from onResponseReceivedActions")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseSearchResponse error: ${e.message}")
        }
        Log.d(TAG, "Search: total items=${items.size}, continuationToken=${continuationToken != null}")
        return BrowseParseResult(items, continuationToken)
    }

    private fun extractVideosFromJson(obj: JsonObject?, items: MutableList<FeedItem>) {
        if (obj == null) return

        // Direct videoRenderer
        obj.getAsJsonObject("videoRenderer")?.let { renderer ->
            items.add(FeedItem.Video(jsonToVideo(renderer)))
        }

        // Compact video
        obj.getAsJsonObject("compactVideoRenderer")?.let { renderer ->
            items.add(FeedItem.Video(jsonToVideo(renderer)))
        }

        // Grid video
        obj.getAsJsonObject("gridVideoRenderer")?.let { renderer ->
            items.add(FeedItem.Video(jsonToVideo(renderer)))
        }

        // Rich item containing video
        obj.getAsJsonObject("richItemRenderer")
            ?.getAsJsonObject("content")
            ?.getAsJsonObject("videoRenderer")?.let { renderer ->
                items.add(FeedItem.Video(jsonToVideo(renderer)))
            }

        // Element renderer (new YouTube format) - recursively search for video data
        obj.getAsJsonObject("elementRenderer")?.let { elem ->
            extractFromElementRenderer(elem, items)
        }

        // Rich section renderer (YouTube 2024+ format)
        obj.getAsJsonObject("richSectionRenderer")?.let { rs ->
            val rsContent = rs.getAsJsonObject("content")
            if (rsContent != null) {
                extractVideosFromJson(rsContent, items)
            }
            rs.getAsJsonArray("contents")?.forEach { child ->
                extractVideosFromJson(child.asJsonObject, items)
            }
        }

        // Shelf with expanded contents
        obj.getAsJsonObject("shelfRenderer")
            ?.getAsJsonObject("content")
            ?.getAsJsonObject("expandedShelfContentsRenderer")
            ?.getAsJsonArray("items")?.forEach { shelfItem ->
                extractVideosFromJson(shelfItem.asJsonObject, items)
            }

        // Continuation
        obj.getAsJsonObject("continuationItemRenderer")?.let {
            // Could extract continuation token here
        }
    }

    private fun extractFromElementRenderer(elem: JsonObject, items: MutableList<FeedItem>) {
        val elemStr = elem.toString()

        // Extract all videoIds with surrounding context
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

        Log.d(TAG, "ElementRenderer: ${videoIds.size} videos, ${titles.size + runsTitles.size} titles")

        videoIds.forEachIndexed { i, vidId ->
            if (vidId.length == 11) {
                val allTitles = titles + runsTitles
                items.add(FeedItem.Video(Video(
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
                )))
            }
        }
    }

    private fun jsonToVideo(renderer: JsonObject): Video {
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
            id = renderer.get("videoId")?.asString ?: "",
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

    override suspend fun search(query: String, continuationToken: String?, params: String?): Result<SearchResult> {
        return try {
            var ctx = webContext()
            val vd = visitorDataBootstrapper.getCachedVisitorData()
            vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }
            val request = com.streamvault.app.data.api.SearchRequest(
                context = ctx,
                query = query,
                params = params
            )
            val rawResponse = apiService.searchRaw(request)
            Log.d(TAG, "Search response code: ${rawResponse.code()}")
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                val result = parseSearchResponse(rawBody)
                Log.d(TAG, "Search parsed ${result.items.size} items")
                Result.success(SearchResult(
                    items = result.items,
                    continuationToken = result.continuationToken
                ))
            } else {
                Result.failure(Exception("Search error: ${rawResponse.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search exception: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getVideoInfo(videoId: String): Result<Video> {
        return try {
            val request = com.streamvault.app.data.api.PlayerRequest(
                context = com.streamvault.app.data.api.ClientContext(
                    client = com.streamvault.app.data.api.ClientInfo(
                        clientName = "ANDROID",
                        clientVersion = "21.03.36",
                        androidSdkVersion = 36,
                        platform = "MOBILE",
                        userAgent = "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip",
                        osName = "Android",
                        osVersion = "16"
                    )
                ),
                videoId = videoId
            )
            val response = apiService.player("com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip", request)
            Log.d(TAG, "Player response code: ${response.code()}")
            if (response.isSuccessful) {
                val playerResponse = response.body()
                val details = playerResponse?.videoDetails
                    ?: return Result.failure(Exception("No video details"))
                Result.success(detailsToVideo(videoId, details))
            } else {
                Result.failure(Exception("Player error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Player exception: ${e.message}")
            Result.failure(e)
        }
    }

    private fun defaultContext() = com.streamvault.app.data.api.ClientContext(
        client = com.streamvault.app.data.api.ClientInfo(
            clientName = "ANDROID",
            clientVersion = "21.03.36",
            androidSdkVersion = 34,
            platform = "MOBILE",
            userAgent = "com.google.android.youtube/21.03.36(Linux; U; Android 14; en_US; sdk_gphone64_arm64 Build/UE1A.230829.036.A1) gzip",
            osName = "Android",
            osVersion = "14"
        )
    )

    private fun webContext() = com.streamvault.app.data.api.ClientContext(
        client = com.streamvault.app.data.api.ClientInfo(
            clientName = "WEB",
            clientVersion = "2.20260623.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )
    )

    override suspend fun getChannelInfo(channelId: String): Result<Channel> {
        return try {
            val request = com.streamvault.app.data.api.BrowseRequest(
                context = defaultContext(),
                browseId = channelId
            )
            val response = apiService.browseRaw(request)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: "{}"
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject

                // Channel metadata from channelMetadataRenderer (most reliable source)
                val metadataChannel = json
                    .getAsJsonObject("metadata")
                    ?.getAsJsonObject("channelMetadataRenderer")

                val title = metadataChannel?.get("title")?.asString ?: channelId

                val avatar = metadataChannel
                    ?.getAsJsonObject("avatar")
                    ?.getAsJsonArray("thumbnails")
                    ?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""

                // Subscriber count from pageHeaderRenderer
                val subscriberCount = try {
                    val pageHeaderViewModel = json
                        .getAsJsonObject("header")
                        ?.getAsJsonObject("pageHeaderRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("pageHeaderViewModel")
                    val metadataRows = pageHeaderViewModel
                        ?.getAsJsonObject("metadata")
                        ?.getAsJsonObject("contentMetadataViewModel")
                        ?.getAsJsonArray("metadataRows")
                    metadataRows?.get(1)?.asJsonObject
                        ?.getAsJsonArray("metadataParts")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("text")
                        ?.get("content")?.asString ?: ""
                } catch (e: Exception) { "" }

                Log.d(TAG, "Channel $channelId: name=$title, subs=$subscriberCount")

                // Parse videos from tabs
                val videos = mutableListOf<com.streamvault.app.domain.model.Video>()
                val tabs = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")

                tabs?.forEach { tab ->
                    val tabRenderer = tab.asJsonObject?.getAsJsonObject("tabRenderer") ?: return@forEach
                    val content = tabRenderer.getAsJsonObject("content")

                    // Try sectionListRenderer path
                    val items = content
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents")

                    items?.forEach { section ->
                        val sectionObj = section.asJsonObject
                        // Try richGridRenderer
                        val richItems = sectionObj.getAsJsonObject("richGridRenderer")
                            ?.getAsJsonArray("contents")
                        richItems?.forEach { item ->
                            extractVideoFromChannelItem(item.asJsonObject, channelId, videos)
                        }
                        // Try itemSectionRenderer
                        val sectionItems = sectionObj.getAsJsonObject("itemSectionRenderer")
                            ?.getAsJsonArray("contents")
                        sectionItems?.forEach { item ->
                            extractVideoFromChannelItem(item.asJsonObject, channelId, videos)
                            // Also handle shelfRenderer inside itemSectionRenderer
                            val shelf = item.asJsonObject?.getAsJsonObject("shelfRenderer") ?: return@forEach
                            val shelfContentRaw = shelf?.get("content")
                            val shelfContentObj = shelfContentRaw as? com.google.gson.JsonObject
                            val hl = shelfContentObj?.getAsJsonObject("horizontalListRenderer")
                            val hlItems = hl?.getAsJsonArray("contents")
                                ?: hl?.getAsJsonArray("items")
                            if (hlItems != null) {
                                hlItems.forEachIndexed { idx, shelfItem ->
                                    extractVideoFromChannelItem(shelfItem.asJsonObject, channelId, videos)
                                }
                            }
                        }
                        // Try playlistVideoListRenderer
                        val playlistVideos = sectionObj.getAsJsonObject("playlistVideoListRenderer")
                            ?.getAsJsonArray("contents")
                        playlistVideos?.forEach { item ->
                            extractVideoFromChannelItem(item.asJsonObject, channelId, videos)
                        }
                        // Try shelfRenderer at section level
                        val topShelf = sectionObj.getAsJsonObject("shelfRenderer")
                        val topShelfContent = topShelf?.getAsJsonObject("content")
                        topShelfContent?.getAsJsonObject("expandedShelfContentsRenderer")
                            ?.getAsJsonArray("contents")?.forEach { shelfItem ->
                                extractVideoFromChannelItem(shelfItem.asJsonObject, channelId, videos)
                            }
                        topShelfContent?.getAsJsonObject("horizontalListRenderer")
                            ?.getAsJsonArray("contents")?.forEach { shelfItem ->
                                extractVideoFromChannelItem(shelfItem.asJsonObject, channelId, videos)
                            }
                    }
                }

                Log.d(TAG, "Channel $channelId: found ${videos.size} videos")
                Result.success(Channel(
                    id = channelId,
                    name = title,
                    avatarUrl = avatar,
                    subscriberCount = subscriberCount,
                    videos = videos
                ))
            } else {
                Result.failure(Exception("Channel error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getChannelInfo error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun extractVideoFromChannelItem(
        itemObj: com.google.gson.JsonObject,
        channelId: String,
        videos: MutableList<com.streamvault.app.domain.model.Video>
    ) {
        val lockupViewModel = itemObj.getAsJsonObject("lockupViewModel")
        if (lockupViewModel != null) {
            try {
                val contentId = lockupViewModel.get("contentId")?.asString
                val metadata = lockupViewModel.get("metadata")?.asJsonObject
                val lockupMeta = metadata?.get("lockupMetadataViewModel")?.asJsonObject
                val titleObj = lockupMeta?.get("title")?.asJsonObject
                val title = titleObj?.let { titleO ->
                    val contentEl = titleO.get("content")
                    when {
                        contentEl?.isJsonObject == true -> contentEl.asJsonObject.get("text")?.asString
                        contentEl?.isJsonPrimitive == true -> contentEl.asString
                        else -> titleO.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text").asString }
                    }
                } ?: ""

                var thumbnailUrl = ""
                try {
                    val thumbObj = lockupViewModel.getAsJsonObject("contentImage")
                        ?.getAsJsonObject("thumbnailViewModel")
                        ?.getAsJsonObject("image")
                    thumbnailUrl = thumbObj?.getAsJsonArray("sources")?.lastOrNull()
                        ?.asJsonObject?.get("url")?.asString ?: ""
                } catch (_: Exception) {}

                var duration = ""
                var viewCount = ""
                var publishedTime = ""
                val metaRows = lockupMeta
                    ?.getAsJsonObject("metadata")
                    ?.getAsJsonObject("contentMetadataViewModel")
                    ?.getAsJsonArray("metadataRows")
                metaRows?.forEach { row ->
                    val parts = row.asJsonObject?.getAsJsonArray("metadataParts")
                    parts?.forEach { part ->
                        val text = part.asJsonObject?.getAsJsonObject("text")?.get("content")?.asString ?: ""
                        when {
                            text.contains(":", ignoreCase = false) && text.matches(Regex(".*\\d+:\\d+")) -> duration = text
                            text.contains("view", ignoreCase = true) -> viewCount = text
                            text.contains("ago", ignoreCase = true) || text.contains("Streamed") -> publishedTime = text
                        }
                    }
                }

                if (duration.isEmpty()) {
                    try {
                        val overlays = lockupViewModel.getAsJsonObject("contentImage")
                            ?.getAsJsonObject("thumbnailViewModel")
                            ?.getAsJsonArray("overlays")
                        overlays?.forEach { overlay ->
                            val badges = overlay.asJsonObject
                                ?.getAsJsonObject("thumbnailOverlayBadgeViewModel")
                                ?.getAsJsonArray("thumbnailBadges")
                            badges?.forEach { badge ->
                                val badgeText = badge.asJsonObject
                                    ?.getAsJsonObject("thumbnailBadgeViewModel")
                                    ?.getAsJsonObject("text")?.get("content")?.asString
                                if (!badgeText.isNullOrEmpty() && duration.isEmpty()) {
                                    duration = badgeText
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (contentId != null) {
                    if (thumbnailUrl.isEmpty()) {
                        thumbnailUrl = "https://i.ytimg.com/vi/$contentId/hqdefault.jpg"
                    }
                    videos.add(com.streamvault.app.domain.model.Video(
                        id = contentId,
                        title = title,
                        channelName = "",
                        channelId = channelId,
                        channelAvatar = "",
                        thumbnailUrl = thumbnailUrl,
                        viewCount = viewCount,
                        publishedTime = publishedTime,
                        duration = duration,
                        isLive = false,
                        isShort = false
                    ))
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error parsing lockupViewModel: ${e.message}")
            }
            return
        }

        // Handle channelVideoPlayerRenderer (YouTube's new channel home format)
        val channelVideoPlayer = itemObj.getAsJsonObject("channelVideoPlayerRenderer")
        if (channelVideoPlayer != null) {
            val videoId = channelVideoPlayer.get("videoId")?.asString ?: return
            val title = extractTextFromJson(channelVideoPlayer, "title") ?: ""
            val viewCount = extractTextFromJson(channelVideoPlayer, "viewCountText") ?: ""
            val publishedTime = extractTextFromJson(channelVideoPlayer, "publishedTimeText") ?: ""
            videos.add(com.streamvault.app.domain.model.Video(
                id = videoId,
                title = title,
                channelName = "",
                channelId = channelId,
                channelAvatar = "",
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                viewCount = viewCount,
                publishedTime = publishedTime,
                duration = "",
                isLive = false,
                isShort = false
            ))
            return
        }

        val videoRenderer = itemObj.getAsJsonObject("videoRenderer")
            ?: itemObj.getAsJsonObject("richItemRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("videoRenderer")
            ?: itemObj.getAsJsonObject("gridVideoRenderer")
            ?: return

        val videoId = videoRenderer.get("videoId")?.asString ?: return
        val videoTitle = extractTextFromJson(videoRenderer, "title") ?: ""
        val channelName = videoRenderer.getAsJsonObject("ownerText")
            ?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: ""
        val thumbnailUrl = videoRenderer.getAsJsonArray("thumbnail")?.get(0)?.asJsonObject
            ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""
        val viewCount = videoRenderer.getAsJsonObject("viewCountText")
            ?.get("simpleText")?.asString ?: ""
        val publishedTime = videoRenderer.getAsJsonObject("publishedTimeText")
            ?.get("simpleText")?.asString ?: ""
        val lengthText = videoRenderer.getAsJsonObject("lengthText")
            ?.get("simpleText")?.asString ?: ""
        val isShort = videoRenderer.getAsJsonArray("badges")?.any { badge ->
            badge.asJsonObject?.getAsJsonObject("metadataBadgeRenderer")
                ?.get("label")?.asString == "Short"
        } ?: false

        videos.add(com.streamvault.app.domain.model.Video(
            id = videoId,
            title = videoTitle,
            channelName = channelName,
            channelId = channelId,
            channelAvatar = "",
            thumbnailUrl = thumbnailUrl,
            viewCount = viewCount,
            publishedTime = publishedTime,
            duration = lengthText,
            isLive = lengthText == "LIVE",
            isShort = isShort
        ))
    }

    private fun extractTextFromJson(obj: com.google.gson.JsonObject?, key: String): String? {
        val element = obj?.getAsJsonObject(key) ?: return null
        return element.get("simpleText")?.asString
            ?: element.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text").asString }
    }

    override suspend fun getPlaylist(playlistId: String): Result<com.streamvault.app.domain.model.Playlist> {
        return try {
            val request = com.streamvault.app.data.api.BrowseRequest(
                context = defaultContext(),
                browseId = "VL$playlistId"
            )
            val response = apiService.browse(request)
            if (response.isSuccessful) {
                val body = response.body()
                val twoColumn = body?.contents?.twoColumnBrowseResultsRenderer
                val header = twoColumn?.header
                val title = extractText(header?.title) ?: ""
                val thumbnail = header?.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""
                val channelName = header?.ownerText?.runs?.firstOrNull()?.text ?: ""

                val items = mutableListOf<FeedItem>()
                twoColumn?.tabs?.forEach { tab ->
                    val sectionContents = tab.content?.sectionListRenderer?.contents
                    sectionContents?.forEach { section ->
                        extractItemsFromSection(section, items)
                    }
                }
                val videos = items.filterIsInstance<FeedItem.Video>().map { it.video }
                Result.success(com.streamvault.app.domain.model.Playlist(
                    id = playlistId,
                    title = title,
                    thumbnailUrl = thumbnail,
                    videoCount = videos.size,
                    channelName = channelName,
                    videos = videos
                ))
            } else {
                Result.failure(Exception("Playlist error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrending(category: String): Result<HomeFeed> {
        return try {
            if (category != "All") {
                Log.d(TAG, "Trending category '$category' requested, using search-based fallback directly")
                return Result.success(fetchSearchBasedTrending(category))
            }

            var ctx = webContext()
            val vd = visitorDataBootstrapper.getCachedVisitorData()
            vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }
            val request = com.streamvault.app.data.api.BrowseRequest(
                context = ctx,
                browseId = "FEtrending"
            )
            val response = apiService.browseRaw(request)
            if (response.isSuccessful) {
                val rawBody = response.body()?.string() ?: ""
                val result = parseBrowseResponse(rawBody)
                if (result.items.isNotEmpty()) {
                    Log.d(TAG, "Trending browse returned ${result.items.size} items")
                    return Result.success(HomeFeed(items = result.items, continuationToken = result.continuationToken))
                }
            } else {
                Log.w(TAG, "Trending browse error: ${response.code()}")
            }

            Log.d(TAG, "Trending browse empty/failed, using search-based fallback")
            val fallback = fetchSearchBasedTrending(category)
            Result.success(fallback)
        } catch (e: Exception) {
            Log.w(TAG, "Trending exception: ${e.message}, using search-based fallback")
            try {
                Result.success(fetchSearchBasedTrending(category))
            } catch (e2: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchSearchBasedTrending(category: String): HomeFeed = withContext(Dispatchers.IO) {
        val query = when (category) {
            "Music" -> "music trending"
            "Gaming" -> "gaming trending"
            "Movies" -> "new movie trailers"
            else -> "trending now"
        }
        return@withContext try {
            val result = search(query, null)
            val items = result.getOrNull()?.items ?: emptyList()
            Log.d(TAG, "fetchSearchBasedTrending('$category') -> '$query': ${items.size} items")
            HomeFeed(items = items, continuationToken = null)
        } catch (e: Exception) {
            Log.w(TAG, "fetchSearchBasedTrending failed: ${e.message}")
            HomeFeed(emptyList(), null)
        }
    }

    override suspend fun getSubscriptions(): Result<HomeFeed> {
        return try {
            var ctx = defaultContext()
            val vd = visitorDataBootstrapper.getCachedVisitorData()
            vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }
            val request = BrowseRequest(
                context = ctx,
                browseId = "FEsubscriptions"
            )
            val response = apiService.browseRaw(request)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: "{}"
                val result = parseBrowseResponse(body)
                if (result.items.isEmpty()) {
                    Log.w(TAG, "Subscriptions feed returned no items, user may not be authenticated")
                    Result.failure(Exception("Sign in to see subscriptions"))
                } else {
                    Result.success(HomeFeed(items = result.items, continuationToken = result.continuationToken))
                }
            } else {
                Log.w(TAG, "Subscriptions feed error: ${response.code()}")
                Result.failure(Exception("Sign in to see subscriptions"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Subscriptions feed exception: ${e.message}")
            Result.failure(Exception("Sign in to see subscriptions"))
        }
    }

    override suspend fun getVideoStreamUrl(videoId: String): Result<String> {
        return try {
            val request = PlayerRequest(
                context = ClientContext(
                    client = ClientInfo(
                        clientName = "ANDROID",
                        clientVersion = "21.03.36",
                        androidSdkVersion = 36,
                        platform = "MOBILE",
                        userAgent = "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip",
                        osName = "Android",
                        osVersion = "16"
                    )
                ),
                videoId = videoId
            )
            val response = apiService.player("com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip", request)
            if (response.isSuccessful) {
                val playerResponse = response.body()
                Log.d(TAG, "Stream formats: ${playerResponse?.streamingData?.formats?.size}, adaptive: ${playerResponse?.streamingData?.adaptiveFormats?.size}")
                val streamUrl = playerResponse?.streamingData?.formats?.firstOrNull()?.url
                    ?: playerResponse?.streamingData?.adaptiveFormats
                        ?.filter { it.mimeType?.startsWith("video/") == true }
                        ?.maxByOrNull { it.height ?: 0 }
                        ?.url
                    ?: playerResponse?.streamingData?.hlsManifestUrl

                if (streamUrl != null) {
                    Result.success(streamUrl)
                } else {
                    Log.w(TAG, "No direct URL from ANDROID API, trying watch page...")
                    val watchPageUrl = tryDecryptWatchPageUrl(videoId)
                    if (watchPageUrl != null) {
                        Result.success(watchPageUrl)
                    } else {
                        val cipherUrl = tryDecryptSingleCipherUrl(playerResponse)
                        if (cipherUrl != null) {
                            Result.success(cipherUrl)
                        } else {
                            Log.w(TAG, "No stream URL found. Status: ${playerResponse?.playabilityStatus?.status}, Reason: ${playerResponse?.playabilityStatus?.reason}")
                            Result.failure(Exception("No stream available: ${playerResponse?.playabilityStatus?.reason ?: "unknown"}"))
                        }
                    }
                }
            } else {
                Result.failure(Exception("Stream error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream exception: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun tryDecryptWatchPageUrl(videoId: String): String? {
        try {
            val watchPageData = fetchWatchPageFormats(videoId)
            val watchPageJson = watchPageData.json
            if (watchPageJson == null) {
                Log.d(TAG, "tryDecryptWatchPage: no watch page JSON")
                return null
            }
            Log.d(TAG, "tryDecryptWatchPage: watch page JSON len=${watchPageJson.length}")

            var cipherOps: List<CipherDecryptor.CipherOp> = emptyList()
            var nTransformOp: NParamDecryptor.NTransformOp? = null

            val jsContent = watchPageData.jsContent
            if (jsContent != null) {
                val decryptor = CipherDecryptor()
                val parsedOps = decryptor.parseOperations(jsContent, CipherDecryptor.OperationStrategy.NAME_LOOKUP)
                cipherOps = if (parsedOps.isNotEmpty()) parsedOps
                else decryptor.parseOperations(jsContent, CipherDecryptor.OperationStrategy.FALLBACK).ifEmpty {
                    CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: emptyList()
                }
                Log.d(TAG, "tryDecryptWatchPage: cipher ops=${cipherOps.size}")

                nTransformOp = NParamDecryptor().parseNTransformCode(jsContent)
                    ?: NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull()
            } else {
                Log.d(TAG, "tryDecryptWatchPage: no player JS, using fallbacks")
                cipherOps = CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: emptyList()
                nTransformOp = NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull()
            }

            val decryptedFormats = streamUrlExtractor.extract(watchPageJson, cipherOps, nTransformOp)
            Log.d(TAG, "tryDecryptWatchPage: decrypted ${decryptedFormats.size} formats")

            return decryptedFormats
                .filter { it.type == com.streamvault.player.youtube.StreamType.PROGRESSIVE || it.mimeType.startsWith("video/") }
                .maxByOrNull { it.height ?: 0 }
                ?.url
        } catch (e: Exception) {
            Log.w(TAG, "tryDecryptWatchPage failed: ${e.message}")
            return null
        }
    }

    private suspend fun tryDecryptSingleCipherUrl(playerResponse: com.streamvault.app.data.api.PlayerResponse?): String? {
        try {
            val gson = com.google.gson.Gson()
            val androidJson = gson.toJson(playerResponse)

            var cipherJs: String? = null
            var nTransformJs: String? = null

            try {
                val webRequest = com.streamvault.app.data.api.PlayerRequest(
                    context = com.streamvault.app.data.api.ClientContext(
                        client = com.streamvault.app.data.api.ClientInfo(
                            clientName = "WEB",
                            clientVersion = "2.20260623.01.00"
                        )
                    ),
                    videoId = playerResponse?.videoDetails?.videoId ?: ""
                )
                val webResponse = apiService.player("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", webRequest)
                if (webResponse.isSuccessful) {
                    val webJson = gson.toJson(webResponse.body())
                    val (fetchedCipherJs, fetchedNTransformJs) = playerJsFetcher.fetchPlayerData(webJson)
                    cipherJs = fetchedCipherJs
                    nTransformJs = fetchedNTransformJs
                }
            } catch (e: Exception) {
                Log.w(TAG, "WEB fetch for cipher JS failed: ${e.message}")
            }

            val cipherOps = if (cipherJs != null) {
                CipherDecryptor().parseOperations(cipherJs, CipherDecryptor.OperationStrategy.NAME_LOOKUP)
            } else {
                CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: return null
            }

            val nTransformOp = if (nTransformJs != null && nTransformJs.isNotEmpty()) {
                NParamDecryptor().parseNTransformCode(nTransformJs)
            } else {
                NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull()
            }

            val decryptedFormats = streamUrlExtractor.extract(androidJson, cipherOps, nTransformOp)

            return decryptedFormats
                .filter { it.type == com.streamvault.player.youtube.StreamType.PROGRESSIVE || it.mimeType.startsWith("video/") }
                .maxByOrNull { it.height ?: 0 }
                ?.url
        } catch (e: Exception) {
            Log.w(TAG, "Cipher URL decryption failed: ${e.message}")
            return null
        }
    }

    private data class WatchPageData(val json: String?, val playerJsUrl: String?, val jsContent: String?)

    private suspend fun fetchWatchPageFormats(videoId: String): WatchPageData {
        return withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://www.youtube.com/watch?v=$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val response = httpClient.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext WatchPageData(null, null, null)

                val jsUrlPatterns = listOf(
                    Regex("/s/player/[a-f0-9]+/player_ias\\.vflset/[^\"]+"),
                    Regex("/s/player/[a-f0-9]+/player_es6\\.vflset/[^\"]+"),
                    Regex("\"jsUrl\":\"(/s/player/[^\"]+)\""),
                    Regex("'jsUrl':'(/s/player/[^']+)'"),
                    Regex("/s/player/[a-f0-9]+/player_ias[^\"]*base\\.js"),
                    Regex("/s/player/[a-f0-9]+/player_es6[^\"]*base\\.js")
                )

                var playerJsUrl: String? = null
                for (p in jsUrlPatterns) {
                    val m = p.find(html)
                    if (m != null) {
                        playerJsUrl = m.groupValues.getOrElse(1) { m.value }
                        break
                    }
                }

                Log.d(TAG, "Watch page: playerJsUrl=${playerJsUrl?.take(80) ?: "NULL"}")

                var jsContent: String? = null
                if (playerJsUrl != null) {
                    try {
                        val pjUrl = playerJsUrl
                        val fullUrl = if (pjUrl.startsWith("http")) pjUrl
                            else "https://www.youtube.com$pjUrl"
                        val jsRequest = okhttp3.Request.Builder()
                            .url(fullUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                        val jsResponse = httpClient.newCall(jsRequest).execute()
                        jsContent = jsResponse.body?.string()
                        Log.d(TAG, "Watch page: fetched player JS len=${jsContent?.length ?: 0}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Watch page: player JS fetch failed: ${e.message}")
                    }
                }

                var jsonStr: String? = null
                try {
                    val apiKeyPattern = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
                    val apiKeyMatch = apiKeyPattern.find(html)
                    val apiKey = apiKeyMatch?.groupValues?.get(1)

                    if (apiKey != null) {
                        Log.d(TAG, "Watch page: found INNERTUBE_API_KEY=$apiKey")
                        val bodyJson = org.json.JSONObject().apply {
                            put("context", org.json.JSONObject().apply {
                                put("client", org.json.JSONObject().apply {
                                    put("clientName", "WEB")
                                    put("clientVersion", "2.20260623.01.00")
                                    put("hl", "en")
                                    put("gl", "US")
                                })
                            })
                            put("videoId", videoId)
                            put("contentCheckOk", true)
                            put("racyCheckOk", true)
                            put("playbackContext", org.json.JSONObject().apply {
                                put("contentPlaybackContext", org.json.JSONObject().apply {
                                    put("signatureTimestamp", 20348)
                                    put("lactMilliseconds", System.currentTimeMillis() % 100000)
                                })
                            })
                        }
                        val apiRequest = okhttp3.Request.Builder()
                            .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
                            .post(bodyJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                            .header("Content-Type", "application/json")
                            .header("Origin", "https://www.youtube.com")
                            .header("Referer", "https://www.youtube.com/watch?v=$videoId")
                            .build()

                        val apiResponse = httpClient.newCall(apiRequest).execute()
                        val apiBody = apiResponse.body?.string()
                        if (apiResponse.isSuccessful && apiBody != null) {
                            jsonStr = apiBody
                            Log.d(TAG, "Watch page: innertube player API response len=${apiBody.length}")
                        } else {
                            Log.w(TAG, "Watch page: innertube player API failed: ${apiResponse.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Watch page: innertube API call failed: ${e.message}")
                }

                if (jsonStr == null) {
                    val patterns = listOf(
                        "var ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});",
                        "ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});",
                        "\"ytInitialPlayerResponse\":\\s*(\\{.+?\\})\\s*[,}]"
                    )
                    for (pattern in patterns) {
                        val regex = Regex(pattern)
                        val match = regex.find(html)
                        if (match != null) {
                            val candidate = match.groupValues[1]
                            try {
                                Gson().fromJson(candidate, JsonObject::class.java)
                                jsonStr = candidate
                                Log.d(TAG, "Watch page: fell back to ytInitialPlayerResponse (len=${jsonStr?.length})")
                                break
                            } catch (e: Exception) {
                                Log.d(TAG, "Watch page JSON parse failed: ${e.message}")
                            }
                        }
                    }
                }

                WatchPageData(jsonStr, playerJsUrl, jsContent)
            } catch (e: Exception) {
                Log.w(TAG, "Watch page fetch failed: ${e.message}")
                WatchPageData(null, null, null)
            }
        }
    }

    override suspend fun getVideoFormats(videoId: String): Result<List<com.streamvault.app.domain.model.VideoFormat>> {
        return try {
            val formats = mutableListOf<com.streamvault.app.domain.model.VideoFormat>()
            val existingItags = mutableSetOf<Int>()
            var cipherSourceJson: String? = null

            data class ClientSpec(
                val name: String,
                val clientInfo: ClientInfo,
                val contextBuilder: (ClientInfo) -> ClientContext = { ClientContext(client = it) }
            )

            val clientChain = listOf(
                ClientSpec(
                    name = "ANDROID_VR",
                    clientInfo = ClientInfo(
                        clientName = "ANDROID_VR",
                        clientVersion = "1.65.10",
                        androidSdkVersion = 32,
                        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                        osName = "Android",
                        osVersion = "12L",
                        deviceMake = "Oculus",
                        deviceModel = "Quest 3"
                    )
                ),
                ClientSpec(
                    name = "TVHTML5",
                    clientInfo = ClientInfo(
                        clientName = "TVHTML5",
                        clientVersion = "7.20260707.07.00",
                        platform = "TV",
                        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
                    ),
                    contextBuilder = { ci -> ClientContext(client = ci, thirdParty = ThirdPartyContext(embedUrl = "https://www.reddit.com/")) }
                ),
                ClientSpec(
                    name = "IOS",
                    clientInfo = ClientInfo(
                        clientName = "IOS",
                        clientVersion = "21.26.4",
                        platform = "MOBILE",
                        userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                        osName = "iPhone",
                        osVersion = "18.3.2.22D82",
                        deviceMake = "Apple",
                        deviceModel = "iPhone16,2"
                    )
                ),
                ClientSpec(
                    name = "ANDROID",
                    clientInfo = ClientInfo(
                        clientName = "ANDROID",
                        clientVersion = "21.26.364",
                        androidSdkVersion = 30,
                        platform = "MOBILE",
                        userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
                        osName = "Android",
                        osVersion = "11"
                    )
                )
            )

            for (spec in clientChain) {
                try {
                    val playbackCtx = PlaybackContext(
                        contentPlaybackContext = ContentPlaybackContext(
                            lactMilliseconds = System.currentTimeMillis() % 100000,
                            currentUrl = "/watch?v=$videoId",
                            signatureTimestamp = 20348
                        )
                    )
                    val request = PlayerRequest(
                        context = spec.contextBuilder(spec.clientInfo),
                        videoId = videoId,
                        playbackContext = playbackCtx
                    )
                    val response = apiService.player(spec.clientInfo.userAgent ?: "com.google.android.youtube/21.03.36 (Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip", request)
                    if (!response.isSuccessful) {
                        Log.w(TAG, "getVideoFormats: ${spec.name} client returned ${response.code()}")
                        continue
                    }
                    val playerResponse = response.body()

                    val hasCipherFormats = playerResponse?.streamingData?.adaptiveFormats
                        ?.any { it.signatureCipher != null || it.cipher != null } == true
                    if (hasCipherFormats) {
                        cipherSourceJson = Gson().toJson(playerResponse)
                        Log.d(TAG, "getVideoFormats: ${spec.name} returned cipher formats, saved as cipher source")
                    }

                    playerResponse?.streamingData?.formats?.forEach { fmt ->
                        if (fmt.url != null && fmt.itag !in existingItags) {
                            existingItags.add(fmt.itag ?: 0)
                            val h = fmt.height ?: 0
                            val label = when {
                                h >= 2160 -> "4K"
                                h >= 1440 -> "1440p"
                                h >= 1080 -> "1080p"
                                h >= 720 -> "720p"
                                h >= 480 -> "480p"
                                h >= 360 -> "360p"
                                h >= 240 -> "240p"
                                h >= 144 -> "144p"
                                h > 0 -> "${h}p"
                                else -> fmt.quality ?: "unknown"
                            }
                            formats.add(
                                com.streamvault.app.domain.model.VideoFormat(
                                    itag = fmt.itag ?: 0,
                                    url = fmt.url,
                                    mimeType = fmt.mimeType ?: "unknown",
                                    bitrate = fmt.bitrate ?: 0,
                                    width = fmt.width,
                                    height = fmt.height,
                                    qualityLabel = label,
                                    isAdaptive = false
                                )
                            )
                        }
                    }

                    playerResponse?.streamingData?.adaptiveFormats?.forEach { fmt ->
                        if (fmt.url != null && fmt.itag !in existingItags) {
                            existingItags.add(fmt.itag ?: 0)
                            val h = fmt.height ?: 0
                            val label = when {
                                h >= 2160 -> "4K"
                                h >= 1440 -> "1440p"
                                h >= 1080 -> "1080p"
                                h >= 720 -> "720p"
                                h >= 480 -> "480p"
                                h >= 360 -> "360p"
                                h >= 240 -> "240p"
                                h >= 144 -> "144p"
                                h > 0 -> "${h}p"
                                else -> fmt.quality ?: "unknown"
                            }
                            formats.add(
                                com.streamvault.app.domain.model.VideoFormat(
                                    itag = fmt.itag ?: 0,
                                    url = fmt.url,
                                    mimeType = fmt.mimeType ?: "unknown",
                                    bitrate = fmt.bitrate ?: 0,
                                    width = fmt.width,
                                    height = fmt.height,
                                    qualityLabel = label,
                                    isAdaptive = true
                                )
                            )
                        }
                    }

                    Log.d(TAG, "getVideoFormats: ${spec.name} client gave ${formats.size} total formats")

                    val maxHeight = formats.maxOfOrNull { it.height ?: 0 } ?: 0
                    if (maxHeight >= 720) {
                        Log.d(TAG, "getVideoFormats: ${spec.name} reached ${maxHeight}p, skipping remaining clients")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "getVideoFormats: ${spec.name} client failed: ${e.message}")
                }
            }

            val maxHeight = formats.maxOfOrNull { it.height ?: 0 } ?: 0
            Log.d(TAG, "getVideoFormats: best quality from clients: ${maxHeight}p (${formats.size} formats)")
            if (maxHeight < 720) {
                Log.d(TAG, "getVideoFormats: best quality ${maxHeight}p < 720p, trying cipher decryption for higher qualities...")
                val watchPageData = fetchWatchPageFormats(videoId)
                val jsContent = watchPageData.jsContent

                val cipherOps: List<CipherDecryptor.CipherOp>
                val nTransformOp: NParamDecryptor.NTransformOp?
                if (jsContent != null) {
                    Log.d(TAG, "getVideoFormats: parsing cipher ops from fetched JS (${jsContent.length} chars)")
                    val decryptor = CipherDecryptor()
                    val parsedOps = decryptor.parseOperations(jsContent, CipherDecryptor.OperationStrategy.NAME_LOOKUP)
                    cipherOps = if (parsedOps.isNotEmpty()) {
                        Log.d(TAG, "getVideoFormats: parsed ${parsedOps.size} cipher ops from JS")
                        parsedOps
                    } else {
                        val parsedFallback = decryptor.parseOperations(jsContent, CipherDecryptor.OperationStrategy.FALLBACK)
                        if (parsedFallback.isNotEmpty()) {
                            Log.d(TAG, "getVideoFormats: fallback parsed ${parsedFallback.size} cipher ops from JS")
                            parsedFallback
                        } else {
                            Log.d(TAG, "getVideoFormats: could not parse cipher ops, using hardcoded fallback")
                            CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: emptyList()
                        }
                    }
                    val nParamDecryptor = NParamDecryptor()
                    val parsedN = nParamDecryptor.parseNTransformCode(jsContent)
                    nTransformOp = if (parsedN != null) {
                        Log.d(TAG, "getVideoFormats: parsed n-transform from JS")
                        parsedN
                    } else {
                        Log.d(TAG, "getVideoFormats: could not parse n-transform, using known algorithm")
                        NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull()
                    }
                } else {
                    Log.d(TAG, "getVideoFormats: no player JS fetched, using hardcoded fallbacks")
                    cipherOps = CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: emptyList()
                    nTransformOp = NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull()
                }

                // PRIMARY: decrypt the ANDROID client's own cipher formats (no PO token required on real devices)
                val cipherSource = cipherSourceJson
                if (cipherSource != null && cipherOps.isNotEmpty()) {
                    val decryptedFormats = streamUrlExtractor.extract(cipherSource, cipherOps, nTransformOp)
                    Log.d(TAG, "getVideoFormats: decrypted ${decryptedFormats.size} ANDROID cipher formats")
                    for (decrypted in decryptedFormats) {
                        if (decrypted.url.isNotEmpty() && decrypted.itag !in existingItags) {
                            existingItags.add(decrypted.itag)
                            formats.add(toVideoFormat(decrypted))
                        }
                    }
                }

                // SECONDARY: WEB watch page (may require a PO token on real devices — kept as fallback)
                val newMax = formats.maxOfOrNull { it.height ?: 0 } ?: 0
                if (newMax < 720) {
                    val watchPageJson = watchPageData.json
                    if (watchPageJson != null) {
                        Log.d(TAG, "getVideoFormats: watch page JSON len=${watchPageJson.length}")
                        val decryptedFormats = streamUrlExtractor.extract(watchPageJson, cipherOps, nTransformOp)
                        Log.d(TAG, "getVideoFormats: decrypted ${decryptedFormats.size} WEB cipher formats")
                        for (decrypted in decryptedFormats) {
                            if (decrypted.url.isNotEmpty() && decrypted.itag !in existingItags) {
                                existingItags.add(decrypted.itag)
                                formats.add(toVideoFormat(decrypted))
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "getVideoFormats: FINAL ${formats.size} formats for $videoId " +
                "(max ${formats.maxOfOrNull { it.height ?: 0 } ?: 0}p)")
            Result.success(formats)
        } catch (e: Exception) {
            Log.w(TAG, "Formats exception: ${e.message}")
            Result.failure(e)
        }
    }

    private fun toVideoFormat(decrypted: com.streamvault.player.youtube.DecryptedStreamFormat): com.streamvault.app.domain.model.VideoFormat {
        val height = decrypted.height
        val width = decrypted.width
        return com.streamvault.app.domain.model.VideoFormat(
            itag = decrypted.itag,
            url = decrypted.url,
            mimeType = decrypted.mimeType,
            bitrate = decrypted.bitrate ?: 0,
            width = width,
            height = height,
            qualityLabel = if (height != null && height > 0) "${height}p" else "unknown",
            isAdaptive = decrypted.type != com.streamvault.player.youtube.StreamType.PROGRESSIVE
        )
    }

    override suspend fun getCaptionTracks(videoId: String): Result<List<com.streamvault.app.domain.model.CaptionTrack>> {
        return try {
            val request = com.streamvault.app.data.api.PlayerRequest(
                context = com.streamvault.app.data.api.ClientContext(
                    client = com.streamvault.app.data.api.ClientInfo(
                        clientName = "ANDROID",
                        clientVersion = "21.03.36",
                        androidSdkVersion = 36,
                        platform = "MOBILE",
                        userAgent = "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip",
                        osName = "Android",
                        osVersion = "16"
                    )
                ),
                videoId = videoId
            )
            val response = apiService.player("com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip", request)
            if (response.isSuccessful) {
                val tracks = response.body()?.captions?.playerCaptionsTracklistRenderer?.captionTracks
                    ?.map { track ->
                        com.streamvault.app.domain.model.CaptionTrack(
                            baseUrl = track.baseUrl ?: "",
                            name = track.name?.simpleText ?: track.languageCode ?: "Unknown",
                            languageCode = track.languageCode ?: "unknown",
                            isTranslatable = track.isTranslatable == true
                        )
                    } ?: emptyList()
                Log.d(TAG, "Found ${tracks.size} caption tracks for $videoId")
                Result.success(tracks)
            } else {
                Result.failure(Exception("Captions error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Captions exception: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getComments(videoId: String): Result<List<com.streamvault.app.domain.model.Comment>> {
        return try {
            val request = NextRequest(
                context = defaultContext(),
                videoId = videoId
            )
            val response = apiService.nextRaw(request)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: "{}"
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject

                val commentsSection = json
                    .getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnWatchNextResults")
                    ?.getAsJsonObject("results")
                    ?.getAsJsonObject("results")
                    ?.getAsJsonArray("contents")
                    ?.lastOrNull()?.asJsonObject
                    ?.getAsJsonObject("itemSectionRenderer")
                    ?.getAsJsonArray("contents")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("continuationItemRenderer")
                    ?.getAsJsonObject("continuationEndpoint")
                    ?.getAsJsonObject("continuationCommand")
                    ?.get("token")?.asString

                if (commentsSection == null) {
                    return Result.success(emptyList())
                }

                val commentsRequest = BrowseRequest(
                    context = defaultContext(),
                    params = commentsSection
                )
                val commentsResponse = apiService.browseRaw(commentsRequest)
                if (commentsResponse.isSuccessful) {
                    val commentsBody = commentsResponse.body()?.string() ?: "{}"
                    val commentsJson = com.google.gson.JsonParser.parseString(commentsBody).asJsonObject
                    val continuationItems = commentsJson
                        .getAsJsonArray("onResponseReceivedEndpoints")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonArray("appendContinuationItemsAction")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonArray("continuationItems")

                    val comments = mutableListOf<com.streamvault.app.domain.model.Comment>()
                    continuationItems?.forEach { item ->
                        val renderer = item.asJsonObject?.getAsJsonObject("commentRenderer")
                        if (renderer != null) {
                            val author = renderer.getAsJsonObject("authorText")?.get("simpleText")?.asString ?: ""
                            val content = renderer.getAsJsonObject("contentText")?.get("simpleText")?.asString ?: ""
                            val votes = renderer.getAsJsonObject("voteCount")?.get("simpleText")?.asString ?: "0"
                            val time = renderer.getAsJsonObject("publishedTimeText")?.get("simpleText")?.asString ?: ""
                            comments.add(com.streamvault.app.domain.model.Comment(author, content, votes, time))
                        }
                    }
                    Log.d(TAG, "Found ${comments.size} comments for $videoId")
                    Result.success(comments)
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Comments exception: ${e.message}")
            Result.success(emptyList())
        }
    }

    override fun getWatchHistory(): Flow<List<Video>> {
        return videoDao.getWatchHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addToWatchHistory(video: Video) {
        videoDao.insertWatchHistory(
            com.streamvault.app.data.local.WatchHistoryEntity.fromDomain(video)
        )
    }

    override suspend fun clearWatchHistory() {
        videoDao.clearWatchHistory()
    }

    override fun getWatchLater(): Flow<List<Video>> {
        return videoDao.getWatchLater().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addToWatchLater(video: Video) {
        videoDao.insertWatchLater(
            com.streamvault.app.data.local.WatchLaterEntity.fromDomain(video)
        )
    }

    override suspend fun clearWatchLater() {
        videoDao.clearWatchLater()
    }

    override fun getSubscriptionsList(): Flow<List<Channel>> {
        return videoDao.getSubscriptions().map { entities ->
            entities.map { Channel(
                id = it.channelId,
                name = it.channelName,
                avatarUrl = it.channelAvatar,
                subscriberCount = ""
            ) }
        }
    }

    override suspend fun subscribeToChannel(channelId: String) {
        videoDao.insertSubscription(
            com.streamvault.app.data.local.SubscriptionEntity(channelId = channelId)
        )
    }

    override suspend fun unsubscribeFromChannel(channelId: String) {
        videoDao.deleteSubscription(channelId)
    }

    override suspend fun getRelatedVideos(videoId: String): Result<List<Video>> {
        return try {
            // Phase 1: WEB client (parsing paths expect WEB-structured JSON)
            var ctx = webContext()
            val vd = visitorDataBootstrapper.getCachedVisitorData()
            vd?.let { ctx = ctx.copy(client = ctx.client.copy(visitorData = it)) }
            val request = NextRequest(context = ctx, videoId = videoId)
            val rawResponse = apiService.nextRaw(request)
            Log.d(TAG, "Related videos response code: ${rawResponse.code()}")
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                val items = parseRelatedResponse(rawBody)
                if (items.isEmpty()) {
                    Log.w(TAG, "No related videos found with WEB client, trying ANDROID...")
                    // Phase 2: ANDROID client with visitorData
                    var androidCtx = defaultContext()
                    vd?.let { androidCtx = androidCtx.copy(client = androidCtx.client.copy(visitorData = it)) }
                    val androidRequest = NextRequest(context = androidCtx, videoId = videoId)
                    val androidResponse = apiService.nextRaw(androidRequest)
                    if (androidResponse.isSuccessful) {
                        val androidBody = androidResponse.body()?.string() ?: ""
                        val androidItems = parseRelatedResponse(androidBody)
                        Result.success(androidItems)
                    } else {
                        Result.success(emptyList())
                    }
                } else {
                    Result.success(items)
                }
            } else {
                val errorBody = rawResponse.errorBody()?.string()?.take(300)
                Log.w(TAG, "Related videos error: ${rawResponse.code()} - $errorBody")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Related videos exception: ${e.message}")
            Result.success(emptyList())
        }
    }

    private fun parseRelatedResponse(rawBody: String): List<Video> {
        val items = mutableListOf<Video>()
        try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(rawBody, com.google.gson.JsonObject::class.java)
            if (root == null) return items

            // Path 1: WEB client - twoColumnWatchNextResults with secondaryResults
            parseTwoColumnSecondary(root, items)

            // Path 2: WEB client - twoColumnWatchNextResults with results.contents
            if (items.isEmpty()) parseTwoColumnResults(root, items)

            // Path 3: ANDROID client - singleColumnWatchNextResults
            if (items.isEmpty()) parseSingleColumnResults(root, items)

            // Path 4: continuationContents (playlist panel)
            if (items.isEmpty()) parseContinuationContents(root, items)

            Log.d(TAG, "Related: total ${items.size} videos")
        } catch (e: Exception) {
            Log.w(TAG, "parseRelatedResponse error: ${e.message}")
        }
        return items
    }

    private fun parseTwoColumnSecondary(root: com.google.gson.JsonObject, items: MutableList<Video>) {
        val secondaryResults = root.getAsJsonObject("contents")
            ?.getAsJsonObject("twoColumnWatchNextResults")
            ?.getAsJsonObject("secondaryResults")
            ?.getAsJsonObject("secondaryResults")
            ?.getAsJsonArray("results") ?: return

        secondaryResults.forEach { item ->
            val obj = item.asJsonObject
            obj.getAsJsonObject("compactVideoRenderer")?.let { renderer ->
                items.add(jsonToVideo(renderer))
            }
            obj.getAsJsonObject("lockupViewModel")?.let { lockup ->
                parseLockupViewModel(lockup)?.let { items.add(it) }
            }
            obj.getAsJsonObject("compactAutoplayRenderer")
                ?.getAsJsonObject("contents")
                ?.getAsJsonArray("contents")?.forEach { c ->
                    c.asJsonObject.getAsJsonObject("compactVideoRenderer")?.let { renderer ->
                        items.add(jsonToVideo(renderer))
                    }
                }
        }
    }

    private fun parseTwoColumnResults(root: com.google.gson.JsonObject, items: MutableList<Video>) {
        val results = root.getAsJsonObject("contents")
            ?.getAsJsonObject("twoColumnWatchNextResults")
            ?.getAsJsonObject("results")
            ?.getAsJsonObject("results")
            ?.getAsJsonArray("contents") ?: return

        results.forEach { content ->
            val obj = content.asJsonObject
            obj.getAsJsonObject("itemSectionRenderer")
                ?.getAsJsonArray("contents")?.forEach { inner ->
                    val innerObj = inner.asJsonObject
                    innerObj.getAsJsonObject("compactVideoRenderer")?.let { renderer ->
                        items.add(jsonToVideo(renderer))
                    }
                    innerObj.getAsJsonObject("lockupViewModel")?.let { lockup ->
                        parseLockupViewModel(lockup)?.let { items.add(it) }
                    }
                }
        }
    }

    private fun parseSingleColumnResults(root: com.google.gson.JsonObject, items: MutableList<Video>) {
        val contentsArr = root.getAsJsonObject("contents")
            ?.getAsJsonObject("singleColumnWatchNextResults")
            ?.getAsJsonObject("results")
            ?.getAsJsonObject("results")
            ?.getAsJsonArray("contents") ?: return

        contentsArr.forEach { content ->
            val obj = content.asJsonObject
            obj.getAsJsonObject("itemSectionRenderer")
                ?.getAsJsonArray("contents")?.forEach { inner ->
                    val innerObj = inner.asJsonObject
                    innerObj.getAsJsonObject("videoRenderer")?.let { renderer ->
                        items.add(jsonToVideo(renderer))
                    }
                    innerObj.getAsJsonObject("compactVideoRenderer")?.let { renderer ->
                        items.add(jsonToVideo(renderer))
                    }
                    innerObj.getAsJsonObject("lockupViewModel")?.let { lockup ->
                        parseLockupViewModel(lockup)?.let { items.add(it) }
                    }
                }
        }
    }

    private fun parseContinuationContents(root: com.google.gson.JsonObject, items: MutableList<Video>) {
        val continuation = root.getAsJsonObject("continuationContents")
            ?.getAsJsonObject("playlistPanelContinuation")
            ?.getAsJsonArray("contents") ?: return

        continuation.forEach { item ->
            val renderer = item.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: return@forEach
            val title = renderer.getAsJsonObject("title")?.get("simpleText")?.asString
                ?: renderer.getAsJsonObject("title")?.getAsJsonArray("runs")
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                ?: ""
            val channel = renderer.getAsJsonObject("shortBylineText")
                ?.getAsJsonArray("runs")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString ?: ""

            items.add(Video(
                id = renderer.get("videoId")?.asString ?: "",
                title = title,
                channelName = channel,
                channelId = "",
                channelAvatar = "",
                thumbnailUrl = renderer.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")?.lastOrNull()
                    ?.asJsonObject?.get("url")?.asString ?: "",
                duration = renderer.getAsJsonObject("lengthText")?.get("simpleText")?.asString ?: "",
                viewCount = renderer.getAsJsonObject("viewCountText")?.get("simpleText")?.asString ?: "",
                publishedTime = renderer.getAsJsonObject("publishedTimeText")?.get("simpleText")?.asString ?: "",
                description = ""
            ))
        }
    }

    private fun parseLockupViewModel(lockup: com.google.gson.JsonObject): Video? {
        val contentId = lockup.get("contentId")?.asString ?: return null
        val contentType = lockup.get("contentType")?.asString ?: ""
        if (contentType != "LOCKUP_CONTENT_TYPE_VIDEO" && contentType != "") return null

        val metadata = lockup.getAsJsonObject("metadata")
            ?.getAsJsonObject("lockupMetadataViewModel")
        val title = metadata?.getAsJsonObject("title")?.get("content")?.asString ?: ""

        val thumbObj = lockup.getAsJsonObject("contentImage")
            ?.getAsJsonObject("thumbnailViewModel")
            ?.getAsJsonObject("image")
        val thumbnailUrl = thumbObj?.getAsJsonArray("sources")?.lastOrNull()
            ?.asJsonObject?.get("url")?.asString ?: ""

        var duration = ""
        var views = ""
        var published = ""
        var channelName = ""

        try {
            val metadataRows = metadata?.getAsJsonObject("metadata")
                ?.getAsJsonObject("contentMetadataViewModel")
                ?.getAsJsonArray("metadataRows")

            metadataRows?.forEach { row ->
                val parts = row.asJsonObject.getAsJsonArray("metadataParts") ?: return@forEach
                parts.forEach { part ->
                    val text = part.asJsonObject.getAsJsonObject("text")?.get("content")?.asString ?: ""
                    if (text.contains(":") && text.matches(Regex(".*\\d+:\\d+"))) {
                        duration = text
                    } else if (text.contains("view")) {
                        views = text
                    } else if (text.contains("ago") || text.contains("year") || text.contains("month") ||
                        text.contains("week") || text.contains("day") || text.contains("hour") ||
                        text.contains("minute") || text.contains("second")) {
                        published = text
                    } else if (channelName.isEmpty() && text.isNotBlank()) {
                        channelName = text
                    }
                }
            }
        } catch (_: Exception) {}

        val overlays = lockup.getAsJsonObject("contentImage")
            ?.getAsJsonObject("thumbnailViewModel")
            ?.getAsJsonArray("overlays")

        if (duration.isEmpty() && overlays != null) {
            try {
                duration = overlays.mapNotNull { overlay ->
                    overlay.asJsonObject.getAsJsonObject("thumbnailOverlayBadgeViewModel")
                        ?.getAsJsonArray("thumbnailBadges")
                        ?.mapNotNull { it.asJsonObject.getAsJsonObject("thumbnailBadgeViewModel")
                            ?.getAsJsonObject("text")?.get("content")?.asString }
                        ?.firstOrNull()
                }.firstOrNull() ?: ""
            } catch (_: Exception) {}
        }

        return Video(
            id = contentId,
            title = title,
            channelName = channelName,
            channelId = "",
            channelAvatar = "",
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            viewCount = views,
            publishedTime = published,
            description = ""
        )
    }

    // === Response Parsing ===

    private fun extractItemsFromSection(
        section: SectionContent,
        items: MutableList<FeedItem>
    ): String? {
        var continuationToken: String? = null

        section.itemSectionRenderer?.contents?.forEach { item ->
            extractItemContent(item, items)
        }

        section.richGridRenderer?.contents?.forEach { richItem ->
            richItem.richItemRenderer?.content?.let { content ->
                content.videoRenderer?.let { renderer ->
                    items.add(FeedItem.Video(videoRendererToVideo(renderer)))
                }
                content.playlistRenderer?.let { renderer ->
                    items.add(FeedItem.Playlist(playlistRendererToPlaylist(renderer)))
                }
            }
        }

        section.shelfRenderer?.content?.let { shelfContent ->
            shelfContent.expandedShelfContentsRenderer?.items?.forEach { shelfItem ->
                shelfItem.videoRenderer?.let { renderer ->
                    items.add(FeedItem.Video(videoRendererToVideo(renderer)))
                }
            }
            shelfContent.gridRenderer?.items?.forEach { gridItem ->
                gridItem.gridVideoRenderer?.let { renderer ->
                    items.add(FeedItem.Video(gridVideoRendererToVideo(renderer)))
                }
            }
            shelfContent.horizontalListRenderer?.items?.forEach { hItem ->
                hItem.videoRenderer?.let { renderer ->
                    items.add(FeedItem.Video(videoRendererToVideo(renderer)))
                }
                hItem.gridVideoRenderer?.let { renderer ->
                    items.add(FeedItem.Video(gridVideoRendererToVideo(renderer)))
                }
            }
        }

        section.continuityItemRenderer?.let {
            continuationToken = "continuation"
        }

        return continuationToken
    }

    private fun extractItemContent(item: ItemContent, items: MutableList<FeedItem>) {
        item.videoRenderer?.let { renderer ->
            items.add(FeedItem.Video(videoRendererToVideo(renderer)))
        }
        item.gridVideoRenderer?.let { renderer ->
            items.add(FeedItem.Video(gridVideoRendererToVideo(renderer)))
        }
        item.richItemRenderer?.content?.let { content ->
            content.videoRenderer?.let { renderer ->
                items.add(FeedItem.Video(videoRendererToVideo(renderer)))
            }
            content.playlistRenderer?.let { renderer ->
                items.add(FeedItem.Playlist(playlistRendererToPlaylist(renderer)))
            }
        }
        item.compactVideoRenderer?.let { renderer ->
            items.add(FeedItem.Video(compactVideoRendererToVideo(renderer)))
        }
        item.playlistRenderer?.let { renderer ->
            items.add(FeedItem.Playlist(playlistRendererToPlaylist(renderer)))
        }
        item.channelRenderer?.let { renderer ->
            items.add(FeedItem.Channel(channelRendererToChannel(renderer)))
        }
        item.radioRenderer?.let { renderer ->
            val title = extractText(renderer.title) ?: ""
            val thumbnail = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""
            items.add(FeedItem.Playlist(com.streamvault.app.domain.model.Playlist(
                id = renderer.playlistId ?: "",
                title = title,
                thumbnailUrl = thumbnail,
                videoCount = renderer.videoCount ?: 0,
                videos = emptyList()
            )))
        }
        item.playlistVideoListRenderer?.contents?.forEach { pvItem ->
            pvItem.playlistVideoRenderer?.let { renderer ->
                items.add(FeedItem.Video(Video(
                    id = renderer.videoId ?: "",
                    title = extractText(renderer.title) ?: "",
                    channelName = renderer.shortBylineText?.runs?.firstOrNull()?.text ?: "",
                    channelId = "",
                    channelAvatar = "",
                    thumbnailUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
                    duration = extractText(renderer.lengthText) ?: "",
                    viewCount = extractText(renderer.viewCountText) ?: "",
                    publishedTime = "",
                    description = ""
                )))
            }
        }
    }

    private fun videoRendererToVideo(renderer: VideoRenderer): Video {
        return Video(
            id = renderer.videoId ?: "",
            title = extractText(renderer.title) ?: "",
            channelName = renderer.ownerText?.runs?.firstOrNull()?.text ?: "",
            channelId = renderer.navigationEndpoint?.watchEndpoint?.playlistId ?: "",
            channelAvatar = "",
            thumbnailUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
            duration = extractText(renderer.lengthText) ?: "",
            viewCount = extractText(renderer.viewCountText) ?: "",
            publishedTime = extractText(renderer.publishedTimeText) ?: "",
            description = ""
        )
    }

    private fun gridVideoRendererToVideo(renderer: GridVideoRenderer): Video {
        return Video(
            id = renderer.videoId ?: "",
            title = extractText(renderer.title) ?: "",
            channelName = renderer.shortBylineText?.runs?.firstOrNull()?.text ?: "",
            channelId = "",
            channelAvatar = "",
            thumbnailUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
            duration = extractText(renderer.lengthText) ?: "",
            viewCount = extractText(renderer.viewCountText) ?: "",
            publishedTime = extractText(renderer.publishedTimeText) ?: "",
            description = ""
        )
    }

    private fun compactVideoRendererToVideo(renderer: CompactVideoRenderer): Video {
        return Video(
            id = renderer.videoId ?: "",
            title = extractText(renderer.title) ?: "",
            channelName = renderer.longBylineText?.runs?.firstOrNull()?.text ?: "",
            channelId = "",
            channelAvatar = "",
            thumbnailUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
            duration = extractText(renderer.lengthText) ?: "",
            viewCount = extractText(renderer.viewCountText) ?: "",
            publishedTime = extractText(renderer.publishedTimeText) ?: "",
            description = ""
        )
    }

    private fun playlistRendererToPlaylist(renderer: PlaylistRenderer): com.streamvault.app.domain.model.Playlist {
        return com.streamvault.app.domain.model.Playlist(
            id = renderer.playlistId ?: "",
            title = extractText(renderer.title) ?: "",
            thumbnailUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
            videoCount = renderer.videoCount ?: 0,
            videos = emptyList()
        )
    }

    private fun channelRendererToChannel(renderer: ChannelRenderer): Channel {
        return Channel(
            id = renderer.channelId ?: "",
            name = extractText(renderer.title) ?: "",
            avatarUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
            subscriberCount = extractText(renderer.subscriberCountText) ?: ""
        )
    }

    private fun detailsToVideo(videoId: String, details: VideoDetails): Video {
        val views = details.viewCount?.toLongOrNull()?.let { count ->
            when {
                count >= 1_000_000 -> "${count / 1_000_000}M views"
                count >= 1_000 -> "${count / 1_000}K views"
                else -> "$count views"
            }
        } ?: ""
        val duration = details.lengthSeconds?.toIntOrNull()?.let { secs ->
            "${secs / 60}:${String.format("%02d", secs % 60)}"
        } ?: ""
        return Video(
            id = videoId,
            title = details.title ?: "",
            channelName = details.author ?: "",
            channelId = details.channelId ?: "",
            channelAvatar = "",
            thumbnailUrl = details.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
            duration = duration,
            viewCount = views,
            publishedTime = "",
            description = details.shortDescription ?: "",
            likeCount = details.likeCount?.toLongOrNull()?.let { count ->
                when {
                    count >= 1_000_000 -> "${count / 1_000_000}M"
                    count >= 1_000 -> "${count / 1_000}K"
                    else -> "$count"
                }
            } ?: ""
        )
    }

    private fun extractText(text: ModelText?): String? {
        if (text == null) return null
        text.simpleText?.let { return it }
        text.runs?.joinToString("") { it.text ?: "" }?.let { return it }
        return null
    }
}