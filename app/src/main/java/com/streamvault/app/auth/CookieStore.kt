package com.streamvault.app.auth

import android.content.Context
import android.util.Log
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
import java.net.HttpURLConnection
import java.net.URL
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
        private const val TAG = "CookieStore"
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

    suspend fun extractCookiesFromOAuth(accessToken: String): Boolean {
        return try {
            val url = URL("https://www.youtube.com/feed/trending")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            val cookies = mutableMapOf<String, String>()
            conn.headerFields.forEach { (key, values) ->
                if (key?.lowercase() == "set-cookie") {
                    values.forEach { cookieHeader ->
                        val parts = cookieHeader.split(";").first().trim().split("=", limit = 2)
                        if (parts.size == 2) {
                            cookies[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
            conn.disconnect()

            val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"]
            if (sapisid != null) {
                val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                save(cookieString, sapisid)
                Log.d(TAG, "Auto-extracted ${cookies.size} cookies from OAuth session")
                true
            } else {
                val sid = cookies["SID"] ?: cookies["__Secure-1PSID"]
                if (sid != null) {
                    val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    save(cookieString, sid)
                    Log.d(TAG, "Saved ${cookies.size} cookies (no SAPISID, using SID)")
                    true
                } else {
                    Log.d(TAG, "No auth cookies found in OAuth response (${responseCode}), ${cookies.size} cookies captured")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cookie extraction from OAuth failed: ${e.message}")
            false
        }
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
