package com.streamvault.app.data.repository

import android.util.Log
import com.streamvault.app.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
open class VisitorDataBootstrapper @Inject constructor(
    @Named("general") private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "VisitorDataBootstrap"
    }

    open suspend fun ensureVisitorData(): Boolean {
        if (NetworkModule.visitorData != null) return true

        return withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://www.youtube.com/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; SM-S908E) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext false

                val vdPattern = Regex("\"visitorData\":\"([^\"]+)\"")
                val vdMatch = vdPattern.find(body)
                if (vdMatch != null) {
                    val vd = vdMatch.groupValues[1]
                    NetworkModule.visitorData = vd
                    Log.d(TAG, "Bootstrapped visitorData=$vd")
                } else {
                    val ytcfgPattern = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
                    val ytcfgMatch = ytcfgPattern.find(body)
                    if (ytcfgMatch != null) {
                        val vd = ytcfgMatch.groupValues[1]
                        NetworkModule.visitorData = vd
                        Log.d(TAG, "Bootstrapped visitorData from ytcfg=$vd")
                    } else {
                        Log.w(TAG, "Could not extract visitorData from YouTube HTML")
                    }
                }

                val apiKeyPattern = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
                val apiKeyMatch = apiKeyPattern.find(body)
                if (apiKeyMatch != null) {
                    val key = apiKeyMatch.groupValues[1]
                    NetworkModule.innerTubeApiKey = key
                    Log.d(TAG, "Bootstrapped innerTubeApiKey=$key")
                }

                NetworkModule.visitorData != null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bootstrap visitorData", e)
                false
            }
        }
    }
}
