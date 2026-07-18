package com.freedomplay.app.download

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.freedomplay.app.data.local.db.DownloadDao
import com.freedomplay.app.data.local.db.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val workManager = WorkManager.getInstance(context)

    fun startDownload(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String,
        quality: String = "720p"
    ) {
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_VIDEO_ID, videoId)
            .putString(DownloadWorker.KEY_TITLE, title)
            .putString(DownloadWorker.KEY_CHANNEL_NAME, channelName)
            .putString(DownloadWorker.KEY_THUMBNAIL_URL, thumbnailUrl)
            .putString(DownloadWorker.KEY_QUALITY, quality)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("download_$videoId")
            .keepResultsForAtLeast(1, TimeUnit.DAYS)
            .build()

        workManager.enqueueUniqueWork(
            "download_$videoId",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    fun pauseDownload(videoId: String) {
        workManager.cancelUniqueWork("download_$videoId")
    }

    fun cancelDownload(videoId: String) {
        workManager.cancelUniqueWork("download_$videoId")
    }

    suspend fun deleteDownload(download: DownloadEntity) {
        val file = java.io.File(download.filePath)
        if (file.exists()) file.delete()
        workManager.cancelUniqueWork("download_${download.videoId}")
        workManager.pruneWork()
        downloadDao.delete(download.videoId)
    }

    fun getDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun observeDownload(videoId: String): Flow<DownloadEntity?> = downloadDao.observeDownload(videoId)
}
