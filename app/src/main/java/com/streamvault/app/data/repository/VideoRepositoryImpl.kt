package com.streamvault.app.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.streamvault.app.data.api.BrowseRequest
import com.streamvault.app.data.api.ClientContext
import com.streamvault.app.data.api.ClientInfo
import com.streamvault.app.data.api.NextRequest
import com.streamvault.app.data.api.PlayerRequest
import com.streamvault.app.data.api.SearchRequest
import com.streamvault.app.data.api.YouTubeApiService
import com.streamvault.app.data.api.VideoDetails
import com.streamvault.app.data.local.VideoDao
import com.streamvault.player.youtube.CipherDecryptor
import com.streamvault.player.youtube.NParamDecryptor
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val apiService: YouTubeApiService,
    private val videoDao: VideoDao
) : VideoRepository {

    private val streamUrlExtractor = StreamUrlExtractor(
        cipherDecryptor = CipherDecryptor(),
        nParamDecryptor = NParamDecryptor()
    )

    companion object {
        private const val TAG = "VideoRepository"
    }

    override suspend fun getHomeFeed(continuationToken: String?): Result<HomeFeed> {
        return try {
            val queries = listOf(
                "trending music 2026",
                "popular videos",
                "technology news",
                "gaming highlights",
                "funny videos",
                "cooking recipes",
                "workout fitness",
                "travel vlog",
                "science documentary",
                "art tutorial",
                "podcast highlights",
                "news today",
                "music mix",
                "movie trailers",
                "sports highlights",
                "programming tutorial",
                "DIY projects",
                "nature wildlife",
                "comedy sketches",
                "motivational speech"
            )
            val index = continuationToken?.toIntOrNull() ?: 0
            val query = queries[index % queries.size]
            Log.d(TAG, "Home feed query[$index]: $query")

            val request = SearchRequest(
                context = defaultContext(),
                query = query
            )
            val rawResponse = apiService.searchRaw(request)
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                val items = parseSearchResponse(rawBody)
                val nextIndex = index + 1
                val nextToken = if (nextIndex < queries.size * 3) nextIndex.toString() else null
                Log.d(TAG, "Home feed parsed ${items.size} items, nextToken=$nextToken")
                Result.success(HomeFeed(
                    items = items,
                    continuationToken = nextToken
                ))
            } else {
                Result.failure(Exception("Home feed error: ${rawResponse.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Home feed exception", e)
            Result.failure(e)
        }
    }

    private fun parseBrowseResponse(rawBody: String): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
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
        } catch (e: Exception) {
            Log.e(TAG, "parseBrowseResponse error", e)
        }
        Log.d(TAG, "parseBrowse: total items=${items.size}")
        return items
    }

    private fun parseSearchResponse(rawBody: String): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
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
        } catch (e: Exception) {
            Log.e(TAG, "parseSearchResponse error", e)
        }
        Log.d(TAG, "Search: total items=${items.size}")
        return items
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

    override suspend fun search(query: String, continuationToken: String?): Result<SearchResult> {
        return try {
            val request = com.streamvault.app.data.api.SearchRequest(
                context = com.streamvault.app.data.api.ClientContext(
                    client = com.streamvault.app.data.api.ClientInfo(
                        clientName = "WEB",
                        clientVersion = "2.20260623.01.00"
                    )
                ),
                query = query
            )
            val rawResponse = apiService.searchRaw(request)
            Log.d(TAG, "Search response code: ${rawResponse.code()}")
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                val items = parseSearchResponse(rawBody)
                Log.d(TAG, "Search parsed ${items.size} items")
                Result.success(SearchResult(
                    items = items,
                    continuationToken = null
                ))
            } else {
                Result.failure(Exception("Search error: ${rawResponse.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search exception", e)
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
            val response = apiService.player(request)
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
            Log.e(TAG, "Player exception", e)
            Result.failure(e)
        }
    }

    private fun defaultContext() = com.streamvault.app.data.api.ClientContext(
        client = com.streamvault.app.data.api.ClientInfo(
            clientName = "WEB",
            clientVersion = "2.20260623.01.00"
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
            Log.e(TAG, "getChannelInfo error", e)
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
                Log.e(TAG, "Error parsing lockupViewModel: ${e.message}", e)
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

    override suspend fun getTrending(): Result<HomeFeed> {
        return try {
            val request = com.streamvault.app.data.api.BrowseRequest(
                context = defaultContext(),
                browseId = "FEtrending"
            )
            val response = apiService.browse(request)
            if (response.isSuccessful) {
                val body = response.body()
                val items = mutableListOf<FeedItem>()
                var nextToken: String? = null

                body?.contents?.twoColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                    val sectionContents = tab.content?.sectionListRenderer?.contents
                    sectionContents?.forEach { section ->
                        nextToken = extractItemsFromSection(section, items) ?: nextToken
                    }
                }
                Result.success(HomeFeed(items = items, continuationToken = nextToken))
            } else {
                Result.failure(Exception("Trending error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSubscriptions(): Result<HomeFeed> {
        return try {
            val request = BrowseRequest(
                context = defaultContext(),
                browseId = "FEsubscriptions"
            )
            val response = apiService.browseRaw(request)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: "{}"
                val items = parseBrowseResponse(body)
                if (items.isEmpty()) {
                    Log.w(TAG, "Subscriptions feed returned no items, user may not be authenticated")
                    Result.failure(Exception("Sign in to see subscriptions"))
                } else {
                    Result.success(HomeFeed(items = items, continuationToken = null))
                }
            } else {
                Log.w(TAG, "Subscriptions feed error: ${response.code()}")
                Result.failure(Exception("Sign in to see subscriptions"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subscriptions feed exception", e)
            Result.failure(Exception("Sign in to see subscriptions"))
        }
    }

    override suspend fun getVideoStreamUrl(videoId: String): Result<String> {
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
            val response = apiService.player(request)
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
                    val cipherUrl = tryDecryptSingleCipherUrl(playerResponse)
                    if (cipherUrl != null) {
                        Result.success(cipherUrl)
                    } else {
                        Log.e(TAG, "No stream URL found. Status: ${playerResponse?.playabilityStatus?.status}, Reason: ${playerResponse?.playabilityStatus?.reason}")
                        Result.failure(Exception("No stream available: ${playerResponse?.playabilityStatus?.reason ?: "unknown"}"))
                    }
                }
            } else {
                Result.failure(Exception("Stream error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream exception", e)
            Result.failure(e)
        }
    }

    private fun tryDecryptSingleCipherUrl(playerResponse: com.streamvault.app.data.api.PlayerResponse?): String? {
        try {
            val cipherOps = CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: return null
            val nTransformOp = NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull() ?: return null

            val gson = com.google.gson.Gson()
            val rawJson = gson.toJson(playerResponse)
            val decryptedFormats = streamUrlExtractor.extract(rawJson, cipherOps, nTransformOp)

            return decryptedFormats
                .filter { it.type == com.streamvault.player.youtube.StreamType.PROGRESSIVE || it.mimeType.startsWith("video/") }
                .maxByOrNull { it.height ?: 0 }
                ?.url
        } catch (e: Exception) {
            Log.w(TAG, "Cipher URL decryption failed: ${e.message}")
            return null
        }
    }

    override suspend fun getVideoFormats(videoId: String): Result<List<com.streamvault.app.domain.model.VideoFormat>> {
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
            val response = apiService.player(request)
            if (response.isSuccessful) {
                val playerResponse = response.body()
                val formats = mutableListOf<com.streamvault.app.domain.model.VideoFormat>()

                playerResponse?.streamingData?.formats?.forEach { fmt ->
                    if (fmt.url != null) {
                        formats.add(
                            com.streamvault.app.domain.model.VideoFormat(
                                itag = fmt.itag ?: 0,
                                url = fmt.url,
                                mimeType = fmt.mimeType ?: "unknown",
                                bitrate = fmt.bitrate ?: 0,
                                width = fmt.width,
                                height = fmt.height,
                                qualityLabel = fmt.quality ?: "${fmt.height}p",
                                isAdaptive = false
                            )
                        )
                    }
                }

                playerResponse?.streamingData?.adaptiveFormats?.forEach { fmt ->
                    if (fmt.url != null) {
                        formats.add(
                            com.streamvault.app.domain.model.VideoFormat(
                                itag = fmt.itag ?: 0,
                                url = fmt.url,
                                mimeType = fmt.mimeType ?: "unknown",
                                bitrate = fmt.bitrate ?: 0,
                                width = fmt.width,
                                height = fmt.height,
                                qualityLabel = fmt.quality ?: "${fmt.height}p",
                                isAdaptive = true
                            )
                        )
                    }
                }

                if (formats.isEmpty()) {
                    Log.d(TAG, "No direct URLs found, attempting cipher decryption via StreamUrlExtractor")
                    tryDecryptCipherFormats(playerResponse, formats)
                }

                Log.d(TAG, "Found ${formats.size} formats for $videoId")
                Result.success(formats)
            } else {
                Result.failure(Exception("Formats error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Formats exception", e)
            Result.failure(e)
        }
    }

    private fun tryDecryptCipherFormats(
        playerResponse: com.streamvault.app.data.api.PlayerResponse?,
        formats: MutableList<com.streamvault.app.domain.model.VideoFormat>
    ) {
        try {
            val streamingData = playerResponse?.streamingData ?: return
            val hasCipherFormats = streamingData.formats?.any { it.signatureCipher != null || it.cipher != null } == true
            val hasCipherAdaptive = streamingData.adaptiveFormats?.any { it.signatureCipher != null || it.cipher != null } == true
            if (!hasCipherFormats && !hasCipherAdaptive) return

            val cipherOps = CipherDecryptor.KNOWN_FALLBACK_PATTERNS["reverse_splice2"] ?: return
            val nTransformOp = NParamDecryptor.KNOWN_ALGORITHMS.firstOrNull() ?: return

            val gson = com.google.gson.Gson()
            val rawJson = gson.toJson(playerResponse)
            val decryptedFormats = streamUrlExtractor.extract(rawJson, cipherOps, nTransformOp)

            for (decrypted in decryptedFormats) {
                formats.add(
                    com.streamvault.app.domain.model.VideoFormat(
                        itag = decrypted.itag,
                        url = decrypted.url,
                        mimeType = decrypted.mimeType,
                        bitrate = decrypted.bitrate ?: 0,
                        width = decrypted.width,
                        height = decrypted.height,
                        qualityLabel = "${decrypted.height}p",
                        isAdaptive = decrypted.type != com.streamvault.player.youtube.StreamType.PROGRESSIVE
                    )
                )
            }
            Log.d(TAG, "Decrypted ${decryptedFormats.size} cipher-protected formats")
        } catch (e: Exception) {
            Log.w(TAG, "Cipher decryption failed: ${e.message}")
        }
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
            val response = apiService.player(request)
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
            Log.e(TAG, "Captions exception", e)
            Result.failure(e)
        }
    }

    override suspend fun getComments(videoId: String): Result<List<com.streamvault.app.domain.model.Comment>> {
        return try {
            val request = NextRequest(
                context = ClientContext(
                    client = ClientInfo(
                        clientName = "WEB",
                        clientVersion = "2.20240101.00.00",
                        platform = "DESKTOP",
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        osName = "Windows",
                        osVersion = "10"
                    )
                ),
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
                    context = ClientContext(
                        client = ClientInfo(
                            clientName = "WEB",
                            clientVersion = "2.20240101.00.00",
                            platform = "DESKTOP",
                            userAgent = "Mozilla/5.0",
                            osName = "Windows",
                            osVersion = "10"
                        )
                    ),
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
            Log.e(TAG, "Comments exception", e)
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
            val request = NextRequest(
                context = defaultContext(),
                videoId = videoId
            )
            val rawResponse = apiService.nextRaw(request)
            Log.d(TAG, "Related videos response code: ${rawResponse.code()}")
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                val items = parseRelatedResponse(rawBody)
                if (items.isEmpty()) {
                    Log.w(TAG, "No related videos found, trying ANDROID client...")
                    val androidRequest = NextRequest(
                        context = com.streamvault.app.data.api.ClientContext(
                            client = com.streamvault.app.data.api.ClientInfo(
                                clientName = "ANDROID",
                                clientVersion = "21.03.36",
                                androidSdkVersion = 36,
                                platform = "MOBILE"
                            )
                        ),
                        videoId = videoId
                    )
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
                Log.e(TAG, "Related videos error: ${rawResponse.code()} - $errorBody")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Related videos exception", e)
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
            Log.e(TAG, "parseRelatedResponse error", e)
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
            description = details.shortDescription ?: ""
        )
    }

    private fun extractText(text: ModelText?): String? {
        if (text == null) return null
        text.simpleText?.let { return it }
        text.runs?.joinToString("") { it.text ?: "" }?.let { return it }
        return null
    }
}