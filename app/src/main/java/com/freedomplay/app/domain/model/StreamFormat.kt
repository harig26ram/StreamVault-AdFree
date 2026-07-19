package com.freedomplay.app.domain.model

data class StreamFormat(
    val url: String?,
    val quality: String?,
    val mimeType: String?,
    val codec: String?,
    val bitrate: Long?,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    /**
     * True for adaptive video-only tracks (no embedded audio). These must be merged with
     * a separate audio track via MergingMediaSource. False for progressive (muxed) streams
     * that already contain audio.
     */
    val isVideoOnly: Boolean = false
)
