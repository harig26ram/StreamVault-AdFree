package com.freedomplay.app.domain.model

data class StreamFormat(
    val url: String?,
    val quality: String?,
    val mimeType: String?,
    val codec: String?,
    val bitrate: Long?,
    val width: Int?,
    val height: Int?,
    val fps: Int?
)
