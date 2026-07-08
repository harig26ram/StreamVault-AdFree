package com.streamvault.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationHelper {

    const val CHANNEL_SUBSCRIPTION_UPLOADS = "subscription_uploads"
    private const val WORK_NAME_SUBSCRIPTION_CHECK = "subscription_check"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val subscriptionChannel = NotificationChannel(
                CHANNEL_SUBSCRIPTION_UPLOADS,
                "Subscription Uploads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new videos from subscribed channels"
            }

            manager.createNotificationChannel(subscriptionChannel)
        }
    }

    fun scheduleSubscriptionCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
            6, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_SUBSCRIPTION_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelSubscriptionCheck(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_NAME_SUBSCRIPTION_CHECK)
    }
}
