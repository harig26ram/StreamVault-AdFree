package com.freedomplay.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.freedomplay.app.data.extractor.OkHttpDownloader
import com.freedomplay.app.data.manager.InstanceManager
import com.freedomplay.app.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class FreedomPlayApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var instanceManager: InstanceManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.recordCrash(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        CrashLogger.init("FreedomPlay")
        CrashLogger.i("App started")

        // Initialise NewPipeExtractor with our shared OkHttp stack. Must run before any
        // extraction call. Cheap + synchronous (just registers the downloader).
        try {
            NewPipe.init(OkHttpDownloader(okHttpClient))
        } catch (e: Exception) {
            CrashLogger.e("NewPipe init failed", e)
        }

        appScope.launch { instanceManager.initialize() }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val downloadChannel = NotificationChannel(
                "downloads",
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress"
            }
            nm.createNotificationChannel(downloadChannel)

            val playbackChannel = NotificationChannel(
                "playback",
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows playback controls"
            }
            nm.createNotificationChannel(playbackChannel)
        }
    }
}
