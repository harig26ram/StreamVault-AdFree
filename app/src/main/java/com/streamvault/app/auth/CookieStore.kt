package com.streamvault.app.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(name = "cookie_store")

@Singleton
class CookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    companion object {
        private val KEY_COOKIES = stringPreferencesKey("youtube_cookies")
        private val KEY_SAPISID = stringPreferencesKey("sapisid")
        private const val XOR_KEY = 0x5A
    }

    init {
        scope.runCatching {
            runBlocking {
                val stored = context.cookieDataStore.data.first()[KEY_COOKIES]
                _isConnected.value = !stored.isNullOrEmpty()
            }
        }
    }

    suspend fun save(cookies: String, sapisid: String) {
        context.cookieDataStore.edit { prefs ->
            prefs[KEY_COOKIES] = xorEncode(cookies)
            prefs[KEY_SAPISID] = xorEncode(sapisid)
        }
        _isConnected.value = true
    }

    fun getCookies(): String? = runBlocking {
        val encoded = context.cookieDataStore.data.first()[KEY_COOKIES] ?: return@runBlocking null
        xorDecode(encoded)
    }

    fun getSapisid(): String? = runBlocking {
        val encoded = context.cookieDataStore.data.first()[KEY_SAPISID] ?: return@runBlocking null
        xorDecode(encoded)
    }

    suspend fun clear() {
        context.cookieDataStore.edit { it.clear() }
        _isConnected.value = false
    }

    internal fun xorEncode(input: String): String {
        return String(input.map { (it.code xor XOR_KEY).toChar() }.toCharArray())
    }

    internal fun xorDecode(input: String): String {
        return String(input.map { (it.code xor XOR_KEY).toChar() }.toCharArray())
    }
}
