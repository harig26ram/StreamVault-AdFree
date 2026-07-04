package com.streamvault.player.core

data class PlayerConfig(
    val preferredVideoWidth: Int = 1920,
    val preferredVideoHeight: Int = 1080,
    var playbackSpeed: Float = 1.0f,
    val bufferSizeBytes: Int = 2 * 1024 * 1024,
    val connectTimeoutMs: Long = 30_000L,
    val readTimeoutMs: Long = 30_000L
)
