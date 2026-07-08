package com.streamvault.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.streamvault.app.R
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.repository.VideoRepository
import com.streamvault.app.presentation.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SubscriptionCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: VideoRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SubscriptionCheckWorker"
        private const val PREFS_NAME = "subscription_notifications"
        private const val KEY_SEEN_VIDEO_IDS = "seen_video_ids"
        private const val NOTIFICATION_ID = 1001
    }

    private fun getPrefs(): SharedPreferences =
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting subscription check")

            val feedResult = repository.getSubscriptions()
            feedResult.fold(
                onSuccess = { feed ->
                    val currentVideos = feed.items.filterIsInstance<FeedItem.Video>()
                    if (currentVideos.isEmpty()) {
                        Log.d(TAG, "No videos in subscription feed")
                        return@fold
                    }

                    val prefs = getPrefs()
                    val seenIds = prefs.getStringSet(KEY_SEEN_VIDEO_IDS, emptySet()) ?: emptySet()

                    val newVideos = currentVideos.filter { it.video.id !in seenIds }

                    if (newVideos.isEmpty()) {
                        Log.d(TAG, "No new videos since last check")
                        return@fold
                    }

                    val newChannelIds = newVideos.map { it.video.channelId }.filter { it.isNotEmpty() }.distinct()
                    val channelCount = newChannelIds.size.coerceAtLeast(1)
                    val videoCount = newVideos.size

                    Log.d(TAG, "Found $videoCount new videos from $channelCount channels")
                    showNotification(videoCount, channelCount)

                    val allIds = currentVideos.map { it.video.id }.toSet()
                    prefs.edit().putStringSet(KEY_SEEN_VIDEO_IDS, allIds).apply()
                },
                onFailure = { e ->
                    Log.w(TAG, "Failed to fetch subscriptions: ${e.message}")
                }
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Subscription check failed", e)
            Result.failure()
        }
    }

    private fun showNotification(videoCount: Int, channelCount: Int) {
        try {
            NotificationHelper.createNotificationChannels(applicationContext)

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "subscriptions")
            }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = applicationContext.getString(R.string.new_uploads_title)
            val body = applicationContext.getString(R.string.new_uploads_summary, videoCount, channelCount)

            val notification = NotificationCompat.Builder(
                applicationContext,
                NotificationHelper.CHANNEL_SUBSCRIPTION_UPLOADS
            )
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot post notification - permission not granted: ${e.message}")
        }
    }
}
