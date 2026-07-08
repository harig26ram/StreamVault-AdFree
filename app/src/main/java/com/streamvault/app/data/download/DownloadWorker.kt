package com.streamvault.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.streamvault.app.R
import com.streamvault.app.data.local.DownloadEntity
import com.streamvault.app.data.local.DownloadStatus
import com.streamvault.app.data.local.VideoDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val videoDao: VideoDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_TITLE = "title"
        const val KEY_CHANNEL_NAME = "channel_name"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_VIDEO_URL = "video_url"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
        private const val TAG = "DownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: ""
        val channelName = inputData.getString(KEY_CHANNEL_NAME) ?: ""
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL) ?: ""
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: ""
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: ""

        createNotificationChannel()

        val existingDownload = videoDao.getDownload(videoId)
        if (existingDownload?.downloadStatus == DownloadStatus.COMPLETED.name) {
            return@withContext Result.success()
        }

        videoDao.insertDownload(
            DownloadEntity(
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                audioUrl = audioUrl,
                videoUrl = videoUrl,
                downloadStatus = DownloadStatus.PENDING.name,
                progress = 0,
                downloadedAt = System.currentTimeMillis()
            )
        )

        try {
            updateNotification(title, 0, "Preparing download...")

            val downloadsDir = File(applicationContext.filesDir, "downloads")
            downloadsDir.mkdirs()

            val audioTempFile = File(downloadsDir, "${videoId}_audio.tmp")
            val videoTempFile = File(downloadsDir, "${videoId}_video.tmp")
            val outputFile = File(downloadsDir, "${videoId}.mp4")

            videoDao.updateDownloadStatus(videoId, DownloadStatus.DOWNLOADING.name, 0)

            // Download audio stream
            updateNotification(title, 0, "Downloading audio...")
            downloadStream(audioUrl, audioTempFile) { progress ->
                setProgressAsync(workDataOf("progress" to progress / 2))
                updateNotification(title, progress / 2, "Downloading audio...")
            }

            if (isStopped) {
                cleanupTempFiles(audioTempFile, videoTempFile)
                videoDao.updateDownloadStatus(videoId, DownloadStatus.PAUSED.name, 0)
                return@withContext Result.success()
            }

            // Download video stream
            updateNotification(title, 50, "Downloading video...")
            downloadStream(videoUrl, videoTempFile) { progress ->
                val totalProgress = 50 + (progress / 2)
                setProgressAsync(workDataOf("progress" to totalProgress))
                updateNotification(title, totalProgress, "Downloading video...")
            }

            if (isStopped) {
                cleanupTempFiles(audioTempFile, videoTempFile)
                videoDao.updateDownloadStatus(videoId, DownloadStatus.PAUSED.name, 50)
                return@withContext Result.success()
            }

            // Mux audio + video
            updateNotification(title, 90, "Processing...")
            muxStreams(audioTempFile, videoTempFile, outputFile)

            val fileSize = outputFile.length()
            videoDao.updateDownloadComplete(videoId, "downloads/${videoId}.mp4", fileSize, DownloadStatus.COMPLETED.name)

            updateNotification(title, 100, "Download complete")

            cleanupTempFiles(audioTempFile, videoTempFile)

            Log.d(TAG, "Download complete: $videoId, size=$fileSize")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $videoId", e)
            videoDao.updateDownloadStatus(videoId, DownloadStatus.FAILED.name, 0)
            updateNotification(title, 0, "Download failed")
            Result.retry()
        }
    }

    private suspend fun downloadStream(url: String, outputFile: File, onProgress: (Int) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.connect()

        val totalBytes = connection.contentLength.toLong()
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }
            }
        }
        connection.disconnect()
    }

    private fun muxStreams(audioFile: File, videoFile: File, outputFile: File) {
        val audioExtractor = MediaExtractor()
        val videoExtractor = MediaExtractor()

        audioExtractor.setDataSource(audioFile.absolutePath)
        videoExtractor.setDataSource(videoFile.absolutePath)

        val audioTrackIndex = findTrackIndex(audioExtractor, false)
        val videoTrackIndex = findTrackIndex(videoExtractor, true)

        if (audioTrackIndex < 0 || videoTrackIndex < 0) {
            audioExtractor.release()
            videoExtractor.release()
            throw IllegalStateException("Could not find tracks in downloaded streams")
        }

        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val audioMuxerTrack = muxer.addTrack(audioFormat)
        val videoMuxerTrack = muxer.addTrack(videoFormat)
        muxer.start()

        muxTrack(audioExtractor, audioTrackIndex, muxer, audioMuxerTrack)
        muxTrack(videoExtractor, videoTrackIndex, muxer, videoMuxerTrack)

        muxer.stop()
        muxer.release()
        audioExtractor.release()
        videoExtractor.release()
    }

    private fun muxTrack(extractor: MediaExtractor, trackIndex: Int, muxer: MediaMuxer, muxerTrackIndex: Int) {
        extractor.selectTrack(trackIndex)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break

            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }

        extractor.unselectTrack(trackIndex)
    }

    private fun findTrackIndex(extractor: MediaExtractor, wantVideo: Boolean): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (wantVideo && mime.startsWith("video/")) return i
            if (!wantVideo && mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun cleanupTempFiles(vararg files: File) {
        files.forEach { it.delete() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video download progress"
            }
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, progress: Int, subtitle: String) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
