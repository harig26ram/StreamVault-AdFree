package com.freedomplay.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "freedomplay_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.prefsDataStore

    object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val AUDIO_ONLY_MODE = booleanPreferencesKey("audio_only_mode")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
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

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
