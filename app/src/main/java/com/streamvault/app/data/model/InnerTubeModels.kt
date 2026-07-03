package com.streamvault.app.data.model

import com.google.gson.annotations.SerializedName

data class InnerTubeResponse(
    val contents: Content?,
    val responseContext: ResponseContext?,
    val trackingParams: String?
)

data class Content(
    val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer?,
    val sectionListRenderer: SectionListRenderer?,
    val itemSectionRenderer: ItemSectionRenderer?
)

data class TwoColumnBrowseResultsRenderer(
    val tabs: List<TabRenderer>?,
    val secondaryContents: SecondaryContents?,
    val header: PlaylistHeaderRenderer?
)

data class PlaylistHeaderRenderer(
    val title: Text?,
    val thumbnail: Thumbnail?,
    val videoCount: Text?,
    val ownerText: OwnerText?,
    val description: Text?
)

data class TabRenderer(
    val tabId: String?,
    val title: String?,
    val content: Content?,
    val endpoint: Endpoint?
)

data class Endpoint(
    val browseEndpoint: BrowseEndpoint?,
    val watchEndpoint: WatchEndpoint?,
    val searchEndpoint: SearchEndpoint?
)

data class BrowseEndpoint(
    val browseId: String?,
    val params: String?
)

data class WatchEndpoint(
    val videoId: String?,
    val playlistId: String?
)

data class SearchEndpoint(
    val query: String?,
    val params: String?
)

data class SecondaryContents(
    val gridRenderer: GridRenderer?,
    val itemSectionRenderer: ItemSectionRenderer?
)

data class SectionListRenderer(
    val contents: List<SectionContent>?
)

data class SectionContent(
    val itemSectionRenderer: ItemSectionRenderer?,
    val shelfRenderer: ShelfRenderer?,
    val richGridRenderer: RichGridRenderer?,
    val continuityItemRenderer: ContinuityItemRenderer?
)

data class ItemSectionRenderer(
    val contents: List<ItemContent>?
)

data class ItemContent(
    val videoRenderer: VideoRenderer?,
    val gridVideoRenderer: GridVideoRenderer?,
    val richItemRenderer: RichItemRenderer?,
    val compactVideoRenderer: CompactVideoRenderer?,
    val radioRenderer: RadioRenderer?,
    val playlistRenderer: PlaylistRenderer?,
    val channelRenderer: ChannelRenderer?,
    val playlistVideoListRenderer: PlaylistVideoListRenderer?
)

data class PlaylistVideoListRenderer(
    val contents: List<PlaylistVideoItem>?,
    val title: Text?,
    val thumbnail: Thumbnail?
)

data class PlaylistVideoItem(
    val playlistVideoRenderer: PlaylistVideoRenderer?
)

data class PlaylistVideoRenderer(
    val videoId: String?,
    val title: Text?,
    val shortBylineText: Text?,
    val thumbnail: Thumbnail?,
    val lengthText: Text?,
    val viewCountText: Text?,
    val index: Text?
)

data class RichItemRenderer(
    val content: ContentItem?
)

data class ContentItem(
    val videoRenderer: VideoRenderer?,
    val playlistRenderer: PlaylistRenderer?
)

data class ShelfRenderer(
    val title: Text?,
    val content: ShelfContent?,
    val endpoint: Endpoint?
)

data class ShelfContent(
    val gridRenderer: GridRenderer?,
    val horizontalListRenderer: HorizontalListRenderer?,
    val expandedShelfContentsRenderer: ExpandedShelfContentsRenderer?
)

data class GridRenderer(
    val items: List<GridItem>?
)

data class GridItem(
    val gridVideoRenderer: GridVideoRenderer?,
    val richItemRenderer: RichItemRenderer?
)

data class HorizontalListRenderer(
    val items: List<HorizontalListItem>?
)

data class HorizontalListItem(
    val gridVideoRenderer: GridVideoRenderer?,
    val videoRenderer: VideoRenderer?,
    val playlistRenderer: PlaylistRenderer?,
    val compactVideoRenderer: CompactVideoRenderer?
)

data class ExpandedShelfContentsRenderer(
    val items: List<ExpandedShelfItem>?
)

data class ExpandedShelfItem(
    val videoRenderer: VideoRenderer?
)

data class ContinuityItemRenderer(
    val items: List<ContinuityItem>?
)

data class ContinuityItem(
    val richItemRenderer: RichItemRenderer?
)

data class GridVideoRenderer(
    val videoId: String?,
    val title: Text?,
    val shortBylineText: Text?,
    val thumbnail: Thumbnail?,
    val publishedTimeText: Text?,
    val viewCountText: Text?,
    val lengthText: Text?,
    val navigationEndpoint: NavigationEndpoint?
)

data class VideoRenderer(
    val videoId: String?,
    val title: Text?,
    val ownerText: OwnerText?,
    val thumbnail: Thumbnail?,
    val lengthText: Text?,
    val viewCountText: Text?,
    val publishedTimeText: Text?,
    val navigationEndpoint: NavigationEndpoint?,
    val badges: List<Badge>?
)

data class CompactVideoRenderer(
    val videoId: String?,
    val title: Text?,
    val longBylineText: Text?,
    val thumbnail: Thumbnail?,
    val lengthText: Text?,
    val viewCountText: Text?,
    val publishedTimeText: Text?,
    val navigationEndpoint: NavigationEndpoint?
)

data class RadioRenderer(
    val playlistId: String?,
    val title: Text?,
    val thumbnail: Thumbnail?,
    val videoCount: Int?,
    val navigationEndpoint: NavigationEndpoint?
)

data class PlaylistRenderer(
    val playlistId: String?,
    val title: Text?,
    val thumbnail: Thumbnail?,
    val videoCount: Int?,
    val navigationEndpoint: NavigationEndpoint?,
    val ownerText: OwnerText?
)

data class ChannelRenderer(
    val channelId: String?,
    val title: Text?,
    val thumbnail: Thumbnail?,
    val subscriberCountText: Text?,
    val navigationEndpoint: NavigationEndpoint?
)

data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint?,
    val browseEndpoint: BrowseEndpoint?,
    val commandMetadata: CommandMetadata?
)

data class CommandMetadata(
    val webCommandMetadata: WebCommandMetadata?
)

data class WebCommandMetadata(
    val url: String?,
    val apiUrl: String?
)

data class Thumbnail(
    val thumbnails: List<ThumbnailItem>?
)

data class ThumbnailItem(
    val url: String?,
    val width: Int?,
    val height: Int?
)

data class Text(
    val simpleText: String?,
    val runs: List<TextRun>?
)

data class TextRun(
    val text: String?,
    val navigationEndpoint: NavigationEndpoint?
)

data class OwnerText(
    val runs: List<TextRun>?
)

data class Badge(
    val metadataBadgeRenderer: MetadataBadgeRenderer?
)

data class MetadataBadgeRenderer(
    val style: String?,
    val label: Text?
)

data class ResponseContext(
    val serviceTrackingParams: List<ServiceTrackingParams>?
)

data class ServiceTrackingParams(
    val service: String?,
    val params: List<TrackingParam>?
)

data class TrackingParam(
    val key: String?,
    val value: String?
)

data class RichGridRenderer(
    val contents: List<RichGridItem>?
)

data class RichGridItem(
    val richItemRenderer: RichItemRenderer?
)