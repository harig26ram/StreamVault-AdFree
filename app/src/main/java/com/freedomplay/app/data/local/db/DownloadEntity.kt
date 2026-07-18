package com.freedomplay.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val filePath: String,
    val fileSize: Long = 0,
    val duration: Long = 0,
    val quality: String = "720p",
    val downloadStatus: String = "QUEUED",
    val progress: Int = 0,
    val downloadedAt: Long = System.currentTimeMillis()
)
