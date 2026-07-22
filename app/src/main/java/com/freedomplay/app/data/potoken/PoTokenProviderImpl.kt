package com.freedomplay.app.data.potoken

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.freedomplay.app.BuildConfig
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

// Ported from NewPipe (GPLv3). Adapted to take an app Context (instead of NewPipe's App.instance),
// to inline the WebView-availability check, and to degrade to null on any failure so that a
// poToken problem falls back to 360p playback rather than crashing the player.
class PoTokenProviderImpl(private val appContext: Context) : PoTokenProvider {

    private val webViewSupported: Boolean by lazy {
        try {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        } catch (e: Exception) {
            false
        }
    }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private object WebPoTokenGenLock
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenGenerator? = null
    private var lastWebPoTokenResult: PoTokenResult? = null

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) {
            return null
        }

        return try {
            getWebClientPoToken(videoId = videoId, forceRecreate = false)
        } catch (e: RuntimeException) {
            // RxJava's Single wraps exceptions into RuntimeErrors, so we need to unwrap them here
            when (val cause = e.cause) {
                is BadWebViewException -> {
                    Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    null
                }
                null -> {
                    Log.e(TAG, "Could not obtain poToken", e)
                    null
                }
                else -> {
                    Log.e(TAG, "Could not obtain poToken", cause)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not obtain poToken", e)
            null
        }
    }

    private fun getWebClientPoToken(videoId: String, forceRecreate: Boolean): PoTokenResult {
        data class Quadruple<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

        val (poTokenGenerator, visitorData, streamingPot, hasBeenRecreated) =
            synchronized(WebPoTokenGenLock) {
                val shouldRecreate = webPoTokenGenerator == null || forceRecreate ||
                    webPoTokenGenerator!!.isExpired()

                if (shouldRecreate) {
                    val innertubeClientRequestInfo = InnertubeClientRequestInfo.ofWebClient()
                    innertubeClientRequestInfo.clientInfo.clientVersion =
                        YoutubeParsingHelper.getClientVersion()

                    webPoTokenVisitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                        innertubeClientRequestInfo,
                        NewPipe.getPreferredLocalization(),
                        NewPipe.getPreferredContentCountry(),
                        YoutubeParsingHelper.getYouTubeHeaders(),
                        YoutubeParsingHelper.YOUTUBEI_V1_URL,
                        null,
                        false
                    )

                    // close the current webPoTokenGenerator on the main thread
                    webPoTokenGenerator?.let { Handler(Looper.getMainLooper()).post { it.close() } }

                    // create a new webPoTokenGenerator
                    webPoTokenGenerator = PoTokenWebView
                        .newPoTokenGenerator(appContext).blockingGet()

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    webPoTokenStreamingPot = webPoTokenGenerator!!
                        .generatePoToken(webPoTokenVisitorData!!).blockingGet()
                }

                return@synchronized Quadruple(
                    webPoTokenGenerator!!,
                    webPoTokenVisitorData!!,
                    webPoTokenStreamingPot!!,
                    shouldRecreate
                )
            }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId).blockingGet()
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                throw throwable
            } else {
                Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
                return getWebClientPoToken(videoId = videoId, forceRecreate = true)
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "poToken for $videoId: playerPot=$playerPot, " +
                    "streamingPot=$streamingPot, visitor_data=$visitorData"
            )
        }

        val result = PoTokenResult(visitorData, playerPot, streamingPot)
        lastWebPoTokenResult = result
        return result
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? {
        return lastWebPoTokenResult
    }

    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    private companion object {
        val TAG: String = PoTokenProviderImpl::class.java.simpleName
    }
}
