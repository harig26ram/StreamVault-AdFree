package com.streamvault.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.streamvault.app.data.download.DownloadManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StreamVaultApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()
        com.streamvault.app.notification.NotificationHelper.createNotificationChannels(this)
        com.streamvault.app.notification.NotificationHelper.scheduleSubscriptionCheck(this)
        CoroutineScope(Dispatchers.IO).launch {
            downloadManager.resumePendingDownloads()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}