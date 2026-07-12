package com.streamvault.app.data.bootstrap

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicReference

@Singleton
open class VisitorDataBootstrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val TAG = "VisitorDataBootstrapper"

    companion object {
        private const val VISITOR_DATA_TTL_MS = 24 * 60 * 60 * 1000L
        private const val YOUTUBE_HOME_URL = "https://www.youtube.com/"
        private const val YOUTUBE_TRENDING_URL = "https://www.youtube.com/feed/trending"
        private const val YOUTUBE_SUGGESTED_URL = "https://www.youtube.com/suggestions"
        private const val CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    }

    private val cachedVisitorData = AtomicReference<String?>(null)
    private val cachedApiKey = AtomicReference<String?>(null)
    private var lastFetchTime = 0L

    private val visitorDataPatterns = listOf(
        Regex("""\"visitorData\":\"([^\"]+)""""),
        Regex("'visitorData':'([^']+)'"),
        Regex("""visitorData\s*:\s*\"([^\"]+)""""),
        Regex("""visitorData\s*:\s*'([^']+)'"""),
        Regex("""\"VISITOR_DATA\":\"([^\"]+)""""),
    )

    private val apiKeyPatterns = listOf(
        Regex("""\"INNERTUBE_API_KEY\":\"([^\"]+)""""),
        Regex("'INNERTUBE_API_KEY':'([^']+)'"),
        Regex("""INNERTUBE_API_KEY\s*:\s*\"([^\"]+)""""),
        Regex("""INNERTUBE_API_KEY\s*:\s*'([^']+)'"""),
        Regex("""\"apiKey\":\"([^\"]+)""""),
    )

    private val fetchLock = Any()

    open suspend fun ensureBootstrapped(): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (now - lastFetchTime < VISITOR_DATA_TTL_MS) {
                val vd = cachedVisitorData.get()
                val key = cachedApiKey.get()
                if (vd != null && key != null) {
                    Log.d(TAG, "Returning cached visitorData (age: ${(now - lastFetchTime) / 1000 / 60} min)")
                    return@withContext Pair(vd, key)
                }
            }

            synchronized(fetchLock) {
                val now2 = System.currentTimeMillis()
                if (now2 - lastFetchTime < VISITOR_DATA_TTL_MS) {
                    val vd = cachedVisitorData.get()
                    val key = cachedApiKey.get()
                    if (vd != null && key != null) {
                        return@withContext Pair(vd, key)
                    }
                }

                fetchFresh()
                return@withContext Pair(cachedVisitorData.get(), cachedApiKey.get())
            }
        }
    }

    private fun fetchFresh() {
        val urls = listOf(YOUTUBE_HOME_URL, YOUTUBE_TRENDING_URL, YOUTUBE_SUGGESTED_URL)

        for (url in urls) {
            try {
                Log.d(TAG, "Fetching visitorData from $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", CHROME_USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                if (html.isNotEmpty()) {
                    val vd = extractVisitorData(html)
                    val key = extractApiKey(html)

                    if (vd != null) {
                        cachedVisitorData.set(vd)
                        Log.d(TAG, "Extracted visitorData (${vd.length} chars) from $url")
                    } else {
                        Log.w(TAG, "Failed to extract visitorData from $url (HTML length: ${html.length})")
                    }

                    if (key != null) {
                        cachedApiKey.set(key)
                        Log.d(TAG, "Extracted API key (${key.length} chars) from $url")
                    } else {
                        Log.w(TAG, "Failed to extract API key from $url")
                    }

                    if (vd != null && key != null) {
                        lastFetchTime = System.currentTimeMillis()
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch failed for $url: ${e.message}")
            }
        }

        Log.e(TAG, "All URL sources failed to provide visitorData/API key")
    }

    private fun extractVisitorData(html: String): String? {
        for (pattern in visitorDataPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                val value = match.groupValues[1]
                if (value.isNotBlank() && value.length > 10) {
                    return value
                }
            }
        }
        return null
    }

    private fun extractApiKey(html: String): String? {
        for (pattern in apiKeyPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                val value = match.groupValues[1]
                if (value.isNotBlank() && value.length > 20) {
                    return value
                }
            }
        }
        return null
    }

    fun getCachedVisitorData(): String? = cachedVisitorData.get()

    fun getCachedApiKey(): String? = cachedApiKey.get()

    fun isCacheValid(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastFetchTime < VISITOR_DATA_TTL_MS) &&
                cachedVisitorData.get() != null &&
                cachedApiKey.get() != null
    }

    fun invalidateCache() {
        cachedVisitorData.set(null)
        cachedApiKey.set(null)
        lastFetchTime = 0
    }
}