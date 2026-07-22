package com.freedomplay.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "freedomplay_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.prefsDataStore

    private val SEARCH_HISTORY_MAX = 20

    object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val AUDIO_ONLY_MODE = booleanPreferencesKey("audio_only_mode")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")
        val PIPED_INSTANCE_URL = stringPreferencesKey("piped_instance_url")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val SAVED_POSITIONS = stringPreferencesKey("saved_positions")
        val YT_COOKIES = stringPreferencesKey("yt_cookies")
    }

    // YouTube account cookies captured at login (main thread, where CookieManager works) and read
    // back synchronously here — reliable, unlike CookieManager from a background/feed thread.
    @Volatile private var cachedYtCookies: String? = null
    @Volatile private var ytCookiesLoaded = false

    val hasYouTubeCookies: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[Keys.YT_COOKIES].isNullOrBlank()
    }

    fun youtubeCookiesBlocking(): String? {
        if (!ytCookiesLoaded) {
            cachedYtCookies = try {
                kotlinx.coroutines.runBlocking { dataStore.data.first()[Keys.YT_COOKIES] }
            } catch (e: Exception) {
                null
            }
            ytCookiesLoaded = true
        }
        return cachedYtCookies?.takeIf { it.isNotBlank() }
    }

    suspend fun setYouTubeCookies(value: String?) {
        cachedYtCookies = value
        ytCookiesLoaded = true
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(Keys.YT_COOKIES) else prefs[Keys.YT_COOKIES] = value
        }
    }

    val theme: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.THEME] ?: "AMOLED"
    }

    val defaultQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_QUALITY] ?: "720p"
    }

    val audioOnlyMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUDIO_ONLY_MODE] ?: false
    }

    val skipSilence: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SKIP_SILENCE] ?: false
    }

    val rememberPosition: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.REMEMBER_POSITION] ?: true
    }

    val autoPlay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_PLAY] ?: true
    }

    val downloadQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_QUALITY] ?: "720p"
    }

    val searchHistory: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY]?.toList()?.takeLast(SEARCH_HISTORY_MAX) ?: emptyList()
    }

    val volumeNormalization: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.VOLUME_NORMALIZATION] ?: false
    }

    val pipedInstanceUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.PIPED_INSTANCE_URL] ?: "https://pipedapi.kavin.rocks/"
    }

    suspend fun setTheme(value: String) {
        dataStore.edit { prefs -> prefs[Keys.THEME] = value }
    }

    suspend fun setDefaultQuality(value: String) {
        dataStore.edit { prefs -> prefs[Keys.DEFAULT_QUALITY] = value }
    }

    suspend fun setAudioOnlyMode(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AUDIO_ONLY_MODE] = value }
    }

    suspend fun setSkipSilence(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.SKIP_SILENCE] = value }
    }

    suspend fun setRememberPosition(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.REMEMBER_POSITION] = value }
    }

    suspend fun setAutoPlay(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AUTO_PLAY] = value }
    }

    suspend fun setDownloadQuality(value: String) {
        dataStore.edit { prefs -> prefs[Keys.DOWNLOAD_QUALITY] = value }
    }

    suspend fun setVolumeNormalization(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.VOLUME_NORMALIZATION] = value }
    }

    suspend fun setPipedInstanceUrl(value: String) {
        dataStore.edit { prefs -> prefs[Keys.PIPED_INSTANCE_URL] = value }
    }

    suspend fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_HISTORY]?.toMutableSet() ?: mutableSetOf()
            current.remove(query)
            current.add(query)
            if (current.size > SEARCH_HISTORY_MAX) {
                prefs[Keys.SEARCH_HISTORY] = current.toList().takeLast(SEARCH_HISTORY_MAX).toSet()
            } else {
                prefs[Keys.SEARCH_HISTORY] = current
            }
        }
    }

    suspend fun removeSearchHistory(query: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_HISTORY]?.toMutableSet() ?: mutableSetOf()
            current.remove(query)
            prefs[Keys.SEARCH_HISTORY] = current
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { prefs -> prefs.remove(Keys.SEARCH_HISTORY) }
    }

    val favorites: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.FAVORITES] ?: emptySet()
    }

    suspend fun setFavorite(videoId: String, isFavorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES] ?: emptySet()
            prefs[Keys.FAVORITES] = if (isFavorite) current + videoId else current - videoId
        }
    }

    suspend fun savePosition(videoId: String, positionMs: Long) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.SAVED_POSITIONS] ?: ""
            val map = mutableMapOf<String, Long>()
            if (raw.isNotEmpty()) {
                raw.split(",").forEach { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2) map[parts[0]] = parts[1].toLongOrNull() ?: 0L
                }
            }
            map[videoId] = positionMs
            prefs[Keys.SAVED_POSITIONS] = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }

    suspend fun getSavedPosition(videoId: String): Long {
        val prefs = dataStore.data.first()
        val raw = prefs[Keys.SAVED_POSITIONS] ?: return 0L
        raw.split(",").forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2 && parts[0] == videoId) {
                return parts[1].toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
