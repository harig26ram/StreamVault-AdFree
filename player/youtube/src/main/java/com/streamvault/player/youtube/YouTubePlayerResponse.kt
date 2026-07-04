package com.streamvault.player.youtube

import com.google.gson.annotations.SerializedName

data class YouTubePlayerResponse(
    @SerializedName("streamingData") val streamingData: YouTubeStreamingData?,
    @SerializedName("videoDetails") val videoDetails: YouTubeVideoDetails?,
    @SerializedName("playabilityStatus") val playabilityStatus: PlayabilityStatus?,
    @SerializedName("captions") val captions: Any?,
    @SerializedName("trackingParams") val trackingParams: String?
)

data class YouTubeStreamingData(
    @SerializedName("formats") val formats: List<YouTubeFormat>?,
    @SerializedName("adaptiveFormats") val adaptiveFormats: List<YouTubeFormat>?,
    @SerializedName("dashManifestUrl") val dashManifestUrl: String?,
    @SerializedName("hlsManifestUrl") val hlsManifestUrl: String?,
    @SerializedName("expiresInSeconds") val expiresInSeconds: String?
)

data class YouTubeFormat(
    @SerializedName("itag") val itag: Int?,
    @SerializedName("url") val url: String?,
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("bitrate") val bitrate: Int?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("initRange") val rangeInit: Range?,
    @SerializedName("indexRange") val rangeIndex: Range?,
    @SerializedName("contentLength") val contentLength: String?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("projectionType") val projectionType: String?,
    @SerializedName("highReplication") val highReplication: Boolean?,
    @SerializedName("audioQuality") val audioQuality: String?,
    @SerializedName("approxDurationMs") val approxDurationMs: String?,
    @SerializedName("audioSampleRate") val audioSampleRate: String?,
    @SerializedName("audioChannels") val audioChannels: Int?,
    @SerializedName("fps") val fps: Int?,
    @SerializedName("signatureCipher") val signatureCipher: String?,
    @SerializedName("cipher") val cipher: String?
)

data class Range(
    @SerializedName("start") val start: String?,
    @SerializedName("end") val end: String?
)

data class YouTubeVideoDetails(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("lengthSeconds") val lengthSeconds: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("channelId") val channelId: String?,
    @SerializedName("shortDescription") val shortDescription: String?,
    @SerializedName("viewCount") val viewCount: String?,
    @SerializedName("isLiveContent") val isLiveContent: Boolean?,
    @SerializedName("isPrivate") val isPrivate: Boolean?,
    @SerializedName("isUpcoming") val isUpcoming: Boolean?
)

data class PlayabilityStatus(
    @SerializedName("status") val status: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("playableInEmbed") val playableInEmbed: Boolean?
)
