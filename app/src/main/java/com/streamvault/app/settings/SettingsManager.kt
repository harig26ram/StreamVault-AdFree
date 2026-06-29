package com.streamvault.app.settings

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "streamvault_settings"

    private lateinit var prefs: SharedPreferences
    private val listeners = mutableMapOf<String, MutableList<() -> Unit>>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun notifyChange(key: String) {
        listeners[key]?.forEach { it() }
    }

    fun addListener(key: String, listener: () -> Unit) {
        listeners.getOrPut(key) { mutableListOf() }.add(listener)
    }

    fun removeListener(key: String, listener: () -> Unit) {
        listeners[key]?.remove(listener)
    }

    var adBlockingEnabled: Boolean
        get() = prefs.getBoolean("ad_blocking", true)
        set(value) { prefs.edit().putBoolean("ad_blocking", value).apply(); notifyChange("ad_blocking") }

    var sponsorBlockEnabled: Boolean
        get() = prefs.getBoolean("sponsor_block", true)
        set(value) { prefs.edit().putBoolean("sponsor_block", value).apply(); notifyChange("sponsor_block") }

    var backgroundPlayEnabled: Boolean
        get() = prefs.getBoolean("background_play", true)
        set(value) { prefs.edit().putBoolean("background_play", value).apply(); notifyChange("background_play") }

    var darkModeEnabled: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) { prefs.edit().putBoolean("dark_mode", value).apply(); notifyChange("dark_mode") }

    var autoplayEnabled: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(value) { prefs.edit().putBoolean("autoplay", value).apply(); notifyChange("autoplay") }

    var watchHistoryEnabled: Boolean
        get() = prefs.getBoolean("watch_history", true)
        set(value) { prefs.edit().putBoolean("watch_history", value).apply(); notifyChange("watch_history") }

    var videoQuality: String
        get() = prefs.getString("video_quality", "1080p") ?: "1080p"
        set(value) { prefs.edit().putString("video_quality", value).apply(); notifyChange("video_quality") }

    var recentSearches: Set<String>
        get() = prefs.getStringSet("recent_searches", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("recent_searches", value).apply(); notifyChange("recent_searches") }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val current = recentSearches.toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > 15) current.removeLast()
        recentSearches = current.toSet()
    }

    fun removeRecentSearch(query: String) {
        val current = recentSearches.toMutableList()
        current.remove(query)
        recentSearches = current.toSet()
    }

    fun clearAllSearches() {
        recentSearches = emptySet()
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        notifyChange("all")
    }
}
