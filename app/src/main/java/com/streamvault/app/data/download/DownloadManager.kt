package com.streamvault.app.data.download

import android.content.Context
import androidx.work.*
import com.streamvault.app.data.local.DownloadEntity
import com.streamvault.app.data.local.DownloadStatus
import com.streamvault.app.data.local.VideoDao
import com.streamvault.app.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao
) {

    fun startDownload(video: Video, audioUrl: String, videoUrl: String) {
        kotlinx.coroutines.runBlocking {
            val existing = videoDao.getDownload(video.id)
            if (existing != null && existing.downloadStatus in listOf(DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name)) {
                videoDao.updateDownloadUrls(video.id, audioUrl, videoUrl)
                videoDao.updateDownloadStatus(video.id, DownloadStatus.PENDING.name, existing.progress)
            } else {
                val entity = DownloadEntity(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    audioUrl = audioUrl,
                    videoUrl = videoUrl,
                    filePath = "",
                    fileSize = 0L,
                    downloadStatus = DownloadStatus.PENDING.name,
                    progress = 0,
                    downloadedAt = System.currentTimeMillis()
                )
                videoDao.insertDownload(entity)
            }
        }

        val data = workDataOf(
            DownloadWorker.KEY_VIDEO_ID to video.id,
            DownloadWorker.KEY_TITLE to video.title,
            DownloadWorker.KEY_CHANNEL_NAME to video.channelName,
            DownloadWorker.KEY_THUMBNAIL_URL to video.thumbnailUrl,
            DownloadWorker.KEY_AUDIO_URL to audioUrl,
            DownloadWorker.KEY_VIDEO_URL to videoUrl
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("download_${video.id}")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "download_${video.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    suspend fun pauseDownload(videoId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$videoId")
        videoDao.updateDownloadStatus(videoId, DownloadStatus.PAUSED.name, -1)
    }

    suspend fun cancelDownload(videoId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$videoId")
        videoDao.updateDownloadStatus(videoId, DownloadStatus.FAILED.name, -1)
    }

    fun getDownload(videoId: String): Flow<DownloadEntity?> {
        return videoDao.observeDownload(videoId)
    }

    fun getAllDownloads(): Flow<List<DownloadEntity>> {
        return videoDao.getAllDownloads()
    }

    suspend fun deleteDownload(videoId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$videoId")
        if (videoDao.getDownload(videoId) == null) return
        try {
            val dir = context.filesDir.resolve("downloads")
            dir.resolve("${videoId}.mp4").delete()
            dir.resolve("${videoId}_audio.tmp").delete()
            dir.resolve("${videoId}_video.tmp").delete()
        } catch (_: Exception) {}
        videoDao.deleteDownload(videoId)
    }

    suspend fun isDownloaded(videoId: String): Boolean {
        val entity = videoDao.getDownload(videoId) ?: return false
        return entity.downloadStatus == DownloadStatus.COMPLETED.name
    }

    suspend fun getLocalFilePath(videoId: String): String? {
        val entity = videoDao.getDownload(videoId) ?: return null
        if (entity.downloadStatus != DownloadStatus.COMPLETED.name) return null
        val file = context.filesDir.resolve(entity.filePath)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun resumePendingDownloads() {
        val pending = videoDao.getPendingDownloads()
        for (entity in pending) {
            if (entity.audioUrl.isEmpty() || entity.videoUrl.isEmpty()) {
                videoDao.updateDownloadStatus(entity.videoId, DownloadStatus.FAILED.name, 0)
                continue
            }
            val data = workDataOf(
                DownloadWorker.KEY_VIDEO_ID to entity.videoId,
                DownloadWorker.KEY_TITLE to entity.title,
                DownloadWorker.KEY_CHANNEL_NAME to entity.channelName,
                DownloadWorker.KEY_THUMBNAIL_URL to entity.thumbnailUrl,
                DownloadWorker.KEY_AUDIO_URL to entity.audioUrl,
                DownloadWorker.KEY_VIDEO_URL to entity.videoUrl
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("download_${entity.videoId}")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "download_${entity.videoId}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}
