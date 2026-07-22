package com.freedomplay.app.domain.usecase

import android.content.Context
import android.content.Intent
import com.freedomplay.app.data.local.db.PlaylistDao
import com.freedomplay.app.data.local.db.PlaylistVideoEntity
import com.freedomplay.app.download.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerActionsUseCase @Inject constructor(
    private val downloadManager: DownloadManager,
    private val playlistDao: PlaylistDao,
    @ApplicationContext private val context: Context
) {
    fun shareVideo(videoId: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://youtube.com/watch?v=$videoId")
        }
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }

    fun downloadVideo(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String,
        quality: String = "720p"
    ) {
        downloadManager.startDownload(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            quality = quality
        )
    }

    suspend fun addToPlaylist(
        videoId: String,
        playlistId: Long,
        title: String,
        channelName: String,
        thumbnailUrl: String,
        duration: Long = 0
    ): Result<Unit> = runCatching {
        playlistDao.addVideo(
            PlaylistVideoEntity(
                playlistId = playlistId,
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                position = playlistDao.getNextPosition(playlistId)
            )
        )
        Unit
    }

    fun likeVideo(): Result<Unit> = Result.failure(
        Exception("Sign in to YouTube to like videos. Go to Settings → Sign In.")
    )

    fun dislikeVideo(): Result<Unit> = Result.failure(
        Exception("Sign in to YouTube to dislike videos. Go to Settings → Sign In.")
    )

    fun subscribe(): Result<Unit> = Result.failure(
        Exception("Sign in to YouTube to subscribe. Go to Settings → Sign In.")
    )
}
