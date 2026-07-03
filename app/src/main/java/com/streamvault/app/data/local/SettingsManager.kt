package com.streamvault.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("streamvault_settings", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var isAmoledMode: Boolean
        get() = prefs.getBoolean("amoled_mode", true)
        set(value) = prefs.edit().putBoolean("amoled_mode", value).apply()

    var videoQuality: String
        get() = prefs.getString("video_quality", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("video_quality", value).apply()

    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(value) = prefs.edit().putBoolean("autoplay", value).apply()

    var defaultTab: String
        get() = prefs.getString("default_tab", "home") ?: "home"
        set(value) = prefs.edit().putString("default_tab", value).apply()

    var swipeBrightness: Boolean
        get() = prefs.getBoolean("swipe_brightness", true)
        set(value) = prefs.edit().putBoolean("swipe_brightness", value).apply()

    var sponsorBlock: Boolean
        get() = prefs.getBoolean("sponsorblock", false)
        set(value) = prefs.edit().putBoolean("sponsorblock", value).apply()

    var miniPlayer: Boolean
        get() = prefs.getBoolean("mini_player", true)
        set(value) = prefs.edit().putBoolean("mini_player", value).apply()

    var backgroundPlay: Boolean
        get() = prefs.getBoolean("background_play", true)
        set(value) = prefs.edit().putBoolean("background_play", value).apply()

    var gestureControls: Boolean
        get() = prefs.getBoolean("gesture_controls", true)
        set(value) = prefs.edit().putBoolean("gesture_controls", value).apply()

    var pinchToZoom: Boolean
        get() = prefs.getBoolean("pinch_to_zoom", true)
        set(value) = prefs.edit().putBoolean("pinch_to_zoom", value).apply()

    var skipSilence: Boolean
        get() = prefs.getBoolean("skip_silence", false)
        set(value) = prefs.edit().putBoolean("skip_silence", value).apply()

    var rememberPlayback: Boolean
        get() = prefs.getBoolean("remember_playback", true)
        set(value) = prefs.edit().putBoolean("remember_playback", value).apply()

    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
