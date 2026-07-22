package com.freedomplay.app.domain.model

data class Stream(
    val title: String,
    val uploader: String,
    val uploaderUrl: String?,
    val thumbnailUrl: String?,
    val duration: Long?,
    val views: Long?,
    val uploaded: Long?,
    val uploadDate: String?,
    val description: String?,
    val videoStreams: List<StreamFormat>,
    val audioStreams: List<StreamFormat>,
    val livestream: Boolean?,
    val subtitles: List<Subtitle>,
    /** Optional DASH manifest URL (adaptive). Preferred by the player when present. */
    val dashManifestUrl: String? = null,
    /** Optional HLS manifest URL (live streams). */
    val hlsManifestUrl: String? = null,
    /** Related/recommended videos for this video (from /next endpoint or Piped streams). */
    val relatedStreams: List<com.freedomplay.app.domain.model.StreamItem> = emptyList()
)
