package com.freedomplay.app

import android.app.Application
import android.util.Log
import com.freedomplay.app.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FreedomPlayApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Install global crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.recordCrash(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        CrashLogger.init("FreedomPlay")
        CrashLogger.i("App started")
    }
}
