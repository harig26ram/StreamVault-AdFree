package com.streamvault.app.settings

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "streamvault_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var adBlockingEnabled: Boolean
        get() = prefs.getBoolean("ad_blocking", true)
        set(value) = prefs.edit().putBoolean("ad_blocking", value).apply()

    var sponsorBlockEnabled: Boolean
        get() = prefs.getBoolean("sponsor_block", true)
        set(value) = prefs.edit().putBoolean("sponsor_block", value).apply()

    var backgroundPlayEnabled: Boolean
        get() = prefs.getBoolean("background_play", true)
        set(value) = prefs.edit().putBoolean("background_play", value).apply()

    var darkModeEnabled: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var autoplayEnabled: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(value) = prefs.edit().putBoolean("autoplay", value).apply()

    var watchHistoryEnabled: Boolean
        get() = prefs.getBoolean("watch_history", true)
        set(value) = prefs.edit().putBoolean("watch_history", value).apply()

    var videoQuality: String
        get() = prefs.getString("video_quality", "1080p") ?: "1080p"
        set(value) = prefs.edit().putString("video_quality", value).apply()

    var lastSearchQuery: String
        get() = prefs.getString("last_search", "") ?: ""
        set(value) = prefs.edit().putString("last_search", value).apply()

    fun clearHistory() {
        prefs.edit().remove("last_search").apply()
    }
}
