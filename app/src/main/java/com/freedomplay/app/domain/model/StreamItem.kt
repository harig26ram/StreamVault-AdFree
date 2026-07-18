package com.freedomplay.app.domain.model

data class StreamItem(
    val url: String,
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val uploaderName: String,
    val uploaderUrl: String?,
    val uploaderAvatar: String?,
    val views: Long,
    val duration: Long?,
    val uploadedDate: String?,
    val uploaded: Long?
)
