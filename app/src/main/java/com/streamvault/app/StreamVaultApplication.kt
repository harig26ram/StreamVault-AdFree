package com.streamvault.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreamVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}