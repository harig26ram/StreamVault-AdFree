package com.freedomplay.app.download

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.freedomplay.app.data.api.piped.PipedApiService
import com.freedomplay.app.data.local.db.DownloadDao
import com.freedomplay.app.data.local.db.DownloadEntity
import com.freedomplay.app.data.repository.StreamRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pipedApi: PipedApiService,
    private val streamRepository: StreamRepository,
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Source-agnostic stream descriptor so both StreamRepository (domain StreamFormat)
     * and the legacy Piped fallback (PipedStream) feed the same download/mux pipeline.
     */
    private data class DownloadableStream(
        val url: String,
        val mimeType: String?,
        val height: Int?,
        val bitrate: Long?,
        /** True for adaptive video-only tracks that need a separate audio track muxed in. */
        val isVideoOnly: Boolean
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return@withContext Result.failure()
        val channelName = inputData.getString(KEY_CHANNEL_NAME) ?: ""
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL) ?: ""
        val quality = inputData.getString(KEY_QUALITY) ?: "720p"

        setForegroundSafely(createForegroundInfo(title))

        try {
            setProgress(workDataOf(KEY_PROGRESS to 5))
            insertOrUpdate(videoId, title, channelName, thumbnailUrl, quality, "DOWNLOADING", 5)

            val candidates = fetchStreams(videoId) ?: return@withContext Result.failure()
            val (audioCandidates, videoCandidates) = candidates

            setProgress(workDataOf(KEY_PROGRESS to 15))

            var audioStream: DownloadableStream? = null
            var videoStream: DownloadableStream? = null

            if (quality.equals("Audio", ignoreCase = true)) {
                audioStream = selectAudioStream(audioCandidates)
                if (audioStream == null) {
                    // Some sources return only a single muxed (audio+video) progressive
                    // stream and zero separate audio tracks. Save that muxed file as-is
                    // rather than failing the "Audio" download.
                    videoStream = videoCandidates.filter { !it.isVideoOnly }
                        .maxByOrNull { it.height ?: 0 }
                }
            } else {
                videoStream = selectVideoStream(videoCandidates, quality)
                if (videoStream?.isVideoOnly == true) {
                    audioStream = selectAudioStream(audioCandidates)
                    if (audioStream == null) {
                        // Video-only track but no audio track to mux with it: prefer a
                        // muxed stream (already contains audio) if one exists.
                        val muxed = selectVideoStream(videoCandidates.filter { !it.isVideoOnly }, quality)
                        if (muxed != null) videoStream = muxed
                    }
                }
                // If videoStream is muxed (isVideoOnly == false) it already carries audio,
                // so it is downloaded alone with no mux step.
                if (videoStream == null) {
                    audioStream = selectAudioStream(audioCandidates)
                }
            }

            if (audioStream == null && videoStream == null) {
                return@withContext Result.failure()
            }

            val cacheDir = File(applicationContext.cacheDir, "downloads/$videoId")
            cacheDir.mkdirs()

            val audioFile = if (audioStream != null) {
                File(cacheDir, "audio.${getExtension(audioStream.mimeType)}")
            } else null

            val videoFile = if (videoStream != null) {
                File(cacheDir, "video.${getExtension(videoStream.mimeType)}")
            } else null

            setProgress(workDataOf(KEY_PROGRESS to 20))

            if (audioStream != null && audioFile != null) {
                downloadFile(audioStream.url, audioFile, 20, 55)
            }

            setProgress(workDataOf(KEY_PROGRESS to 55))

            if (videoStream != null && videoFile != null) {
                downloadFile(videoStream.url, videoFile, 55, 85)
            }

            setProgress(workDataOf(KEY_PROGRESS to 85))

            val safeName = sanitizeFileName("$title - $channelName")
            val outputFile = if (audioFile != null && videoFile != null) {
                muxFiles(audioFile, videoFile, videoId, safeName)
            } else if (audioFile != null) {
                saveToDownloads(audioFile, videoId, safeName)
            } else if (videoFile != null) {
                saveToDownloads(videoFile, videoId, safeName)
            } else null

            if (outputFile == null) {
                return@withContext Result.failure()
            }

            val fileSize = outputFile.length()
            insertOrUpdate(videoId, title, channelName, thumbnailUrl, quality, "COMPLETED", 100, outputFile.absolutePath, fileSize)

            cacheDir.deleteRecursively()

            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success()

        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("ENOSPC") == true) {
                return@withContext Result.failure()
            }
            if (isStopped) {
                cleanupPartial(videoId)
                return@withContext Result.failure()
            }
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(
        inputData.getString(KEY_TITLE) ?: "Downloading"
    )

private fun createForegroundInfo(title: String): ForegroundInfo {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
            android.app.Notification.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Downloading")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
                .setContentTitle("Downloading")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
} else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun setForegroundSafely(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (_: Exception) { }
    }

    private suspend fun downloadFile(url: String, output: File, progressStart: Int, progressEnd: Int) {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            val body = resp.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L

            body.byteStream().use { input ->
                FileOutputStream(output).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var bytes: Int

                    while (input.read(buffer).also { bytes = it } != -1) {
                        if (isStopped) throw IOException("Cancelled")
                        outputStream.write(buffer, 0, bytes)
                        bytesRead += bytes

                        if (totalBytes > 0) {
                            val progress = progressStart + ((bytesRead.toFloat() / totalBytes) * (progressEnd - progressStart)).toInt()
                            setProgress(workDataOf(KEY_PROGRESS to progress.coerceIn(progressStart, progressEnd)))
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves audio/video stream candidates for [videoId].
     *
     * Primary source is [StreamRepository] (cascading NewPipeExtractor -> Piped ->
     * InnerTube -> Invidious fallback); the direct Piped API is only used if the
     * repository yields nothing, since most public Piped instances are unreliable.
     *
     * @return Pair(audioCandidates, videoCandidates), or null if the video cannot
     *         be downloaded (e.g. livestream or no usable streams from any source).
     */
    private suspend fun fetchStreams(
        videoId: String
    ): Pair<List<DownloadableStream>, List<DownloadableStream>>? {
        val repoStream = runCatching { streamRepository.getStreams(videoId) }
            .getOrNull()?.getOrNull()
        if (repoStream != null) {
            if (repoStream.livestream == true) return null
            val audio = repoStream.audioStreams
                .filter { !it.url.isNullOrBlank() }
                .map { DownloadableStream(it.url!!, it.mimeType, it.height, it.bitrate, isVideoOnly = false) }
            val video = repoStream.videoStreams
                .filter { !it.url.isNullOrBlank() }
                .map { DownloadableStream(it.url!!, it.mimeType, it.height, it.bitrate, it.isVideoOnly) }
            if (audio.isNotEmpty() || video.isNotEmpty()) return audio to video
        }

        // Legacy fallback: direct Piped API. Exceptions propagate to doWork's catch
        // so transient network failures still trigger Result.retry().
        val response = pipedApi.getVideoStreams(videoId)
        if (response.livestream == true) return null
        val audio = response.audioStreams.orEmpty()
            .filter { it.mimeType?.startsWith("audio") == true && !it.url.isNullOrBlank() }
            .map { DownloadableStream(it.url!!, it.mimeType, it.height, it.bitrate, isVideoOnly = false) }
        // Piped's videoStreams are adaptive (video-only) tracks, matching the previous
        // behavior of always muxing them with a separate audio track.
        val video = response.videoStreams.orEmpty()
            .filter { it.mimeType?.startsWith("video") == true && !it.url.isNullOrBlank() && it.height != null }
            .map { DownloadableStream(it.url!!, it.mimeType, it.height, it.bitrate, isVideoOnly = true) }
        if (audio.isEmpty() && video.isEmpty()) return null
        return audio to video
    }

    private fun selectAudioStream(streams: List<DownloadableStream>): DownloadableStream? {
        // Prefer MP4/M4A audio: MediaMuxer outputs MPEG-4, and opus/webm tracks
        // frequently fail to mux into it.
        val mp4Audio = streams.filter {
            it.mimeType?.contains("mp4") == true || it.mimeType?.contains("m4a") == true
        }
        return mp4Audio.ifEmpty { streams }.maxByOrNull { it.bitrate ?: 0L }
    }

    private fun selectVideoStream(streams: List<DownloadableStream>, quality: String): DownloadableStream? {
        val targetHeight = when (quality) {
            "144p" -> 144; "240p" -> 240; "360p" -> 360
            "480p" -> 480; "720p" -> 720; "1080p" -> 1080
            "1440p" -> 1440; "2160p" -> 2160
            else -> 720
        }
        return streams.filter { it.height != null }
            .sortedWith(
                compareBy(
                    { abs(it.height!! - targetHeight) },
                    // Tie-break in favor of MP4/AVC so the MPEG-4 mux step succeeds.
                    { if (it.mimeType?.contains("mp4") == true) 0 else 1 }
                )
            )
            .firstOrNull()
            ?: streams.firstOrNull()
    }

    private fun getExtension(mimeType: String?): String {
        val mime = mimeType ?: return "mp4"
        return when {
            mime.startsWith("audio") ->
                if (mime.contains("webm") || mime.contains("opus")) "webm" else "m4a"
            mime.contains("webm") -> "webm"
            mime.contains("3gpp") -> "3gp"
            else -> "mp4"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .replace(Regex("\\.{2,}"), "_")
            .trim()
            .take(200)
            .ifBlank { "download" }
    }

    private fun muxFiles(audioFile: File, videoFile: File, @Suppress("UNUSED_PARAMETER") videoId: String, displayName: String): File? {
        return try {
            val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }

            val audioTrackIndex = findTrackIndex(audioExtractor, "audio/")
            val videoTrackIndex = findTrackIndex(videoExtractor, "video/")

            if (audioTrackIndex < 0 && videoTrackIndex < 0) return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val outputFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FreedomPlay/$displayName.mp4"
                )
                outputFile.parentFile?.mkdirs()

                val muxer = MediaMuxer(
                    outputFile.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

                var audioMuxerTrack = -1
                var videoMuxerTrack = -1

                if (audioTrackIndex >= 0) {
                    audioExtractor.selectTrack(audioTrackIndex)
                    val format = audioExtractor.getTrackFormat(audioTrackIndex)
                    audioMuxerTrack = muxer.addTrack(format)
                }

                if (videoTrackIndex >= 0) {
                    videoExtractor.selectTrack(videoTrackIndex)
                    val format = videoExtractor.getTrackFormat(videoTrackIndex)
                    videoMuxerTrack = muxer.addTrack(format)
                }

                try {
                    muxer.start()
                    val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
                    val info = android.media.MediaCodec.BufferInfo()

                    if (videoTrackIndex >= 0) {
                        while (true) {
                            val chunkSize = videoExtractor.readSampleData(buffer, 0)
                            if (chunkSize < 0) break
                            info.set(0, chunkSize, videoExtractor.sampleTime, videoExtractor.sampleFlags)
                            muxer.writeSampleData(videoMuxerTrack, buffer, info)
                            videoExtractor.advance()
                        }
                    }

                    if (audioTrackIndex >= 0) {
                        while (true) {
                            val chunkSize = audioExtractor.readSampleData(buffer, 0)
                            if (chunkSize < 0) break
                            info.set(0, chunkSize, audioExtractor.sampleTime, audioExtractor.sampleFlags)
                            muxer.writeSampleData(audioMuxerTrack, buffer, info)
                            audioExtractor.advance()
                        }
                    }

                    muxer.stop()
                } finally {
                    try { muxer.release() } catch (_: Exception) {}
                    try { audioExtractor.release() } catch (_: Exception) {}
                    try { videoExtractor.release() } catch (_: Exception) {}
                }

                addToMediaStore(outputFile, "video/mp4", displayName)
                outputFile
            } else {
                val outputFile = File(
                    applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "$displayName.mp4"
                )
                outputFile.parentFile?.mkdirs()

                val muxer = MediaMuxer(
                    outputFile.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

                var audioMuxerTrack = -1
                var videoMuxerTrack = -1

                if (audioTrackIndex >= 0) {
                    audioExtractor.selectTrack(audioTrackIndex)
                    audioMuxerTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrackIndex))
                }

                if (videoTrackIndex >= 0) {
                    videoExtractor.selectTrack(videoTrackIndex)
                    videoMuxerTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrackIndex))
                }

                try {
                    muxer.start()
                    val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
                    val info = android.media.MediaCodec.BufferInfo()

                    if (videoTrackIndex >= 0) {
                        while (true) {
                            val chunkSize = videoExtractor.readSampleData(buffer, 0)
                            if (chunkSize < 0) break
                            info.set(0, chunkSize, videoExtractor.sampleTime, videoExtractor.sampleFlags)
                            muxer.writeSampleData(videoMuxerTrack, buffer, info)
                            videoExtractor.advance()
                        }
                    }

                    if (audioTrackIndex >= 0) {
                        while (true) {
                            val chunkSize = audioExtractor.readSampleData(buffer, 0)
                            if (chunkSize < 0) break
                            info.set(0, chunkSize, audioExtractor.sampleTime, audioExtractor.sampleFlags)
                            muxer.writeSampleData(audioMuxerTrack, buffer, info)
                            audioExtractor.advance()
                        }
                    }

                    muxer.stop()
                } finally {
                    try { muxer.release() } catch (_: Exception) {}
                    try { audioExtractor.release() } catch (_: Exception) {}
                    try { videoExtractor.release() } catch (_: Exception) {}
                }

                outputFile
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findTrackIndex(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun saveToDownloads(source: File, @Suppress("UNUSED_PARAMETER") videoId: String, displayName: String): File? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val outputFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FreedomPlay/$displayName.${source.extension}"
                )
                outputFile.parentFile?.mkdirs()
                source.copyTo(outputFile, overwrite = true)
                addToMediaStore(outputFile, getMimeType(source.extension), displayName)
                outputFile
            } else {
                val outputFile = File(
                    applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "$displayName.${source.extension}"
                )
                outputFile.parentFile?.mkdirs()
                source.copyTo(outputFile, overwrite = true)
                outputFile
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun addToMediaStore(file: File, mimeType: String, @Suppress("UNUSED_PARAMETER") displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FreedomPlay")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = applicationContext.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                }
            } catch (_: Exception) { }
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "mp4" -> "video/mp4"; "webm" -> "video/webm"
            "m4a" -> "audio/mp4"; "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    private suspend fun insertOrUpdate(
        videoId: String, title: String, channelName: String,
        thumbnailUrl: String, quality: String, status: String,
        progress: Int, filePath: String = "", fileSize: Long = 0
    ) {
        val existing = downloadDao.getDownload(videoId)
        val entity = existing?.copy(
            filePath = filePath.ifEmpty { existing.filePath },
            fileSize = fileSize.takeIf { it > 0 } ?: existing.fileSize,
            quality = quality,
            downloadStatus = status,
            progress = progress,
            downloadedAt = System.currentTimeMillis()
        ) ?: DownloadEntity(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            filePath = filePath,
            fileSize = fileSize,
            quality = quality,
            downloadStatus = status,
            progress = progress
        )
        downloadDao.upsert(entity)
    }

    private fun cleanupPartial(videoId: String) {
        val cacheDir = File(applicationContext.cacheDir, "downloads/$videoId")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2001
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_TITLE = "title"
        const val KEY_CHANNEL_NAME = "channel_name"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
    }
}
