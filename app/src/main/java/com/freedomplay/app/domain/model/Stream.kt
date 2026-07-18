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
    val subtitles: List<Subtitle>
)
