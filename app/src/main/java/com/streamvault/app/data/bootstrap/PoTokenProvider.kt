package com.streamvault.app.data.bootstrap

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Generates YouTube PO (Proof of Origin) tokens via BotGuard WebView execution.
 *
 * Based on NewPipe's PoTokenWebView implementation:
 * 1. POST /api/jnn/v1/Create → BotGuard challenge data
 * 2. Execute BotGuard in hidden WebView (loads po_token.html asset)
 * 3. POST /api/jnn/v1/GenerateIT → integrityToken
 * 4. Use integrityToken to mint PO tokens (content-bound + session-bound)
 *
 * Token types:
 * - Content-bound (videoId): for PlayerRequest.serviceIntegrityToken
 * - Session-bound (visitorData): appended as &pot= to streaming URLs
 */
@Singleton
class PoTokenProvider @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    @Named("general") private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "PoTokenProvider"
        private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val WEBVIEW_TIMEOUT_MS = 30_000L
        private const val MINT_TIMEOUT_MS = 5_000L
    }

    data class PoTokenResult(
        val playerRequestPoToken: String,
        val streamingPoToken: String
    )

    // WebView thread
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var webView: WebView? = null

    // Session state
    @Volatile
    private var sessionIntegrityToken: String? = null
    @Volatile
    private var sessionStreamingToken: String? = null
    private val perVideoTokens = mutableMapOf<String, String>()
    private val lock = Any()

    // WebView readiness
    private var webViewReady = CompletableDeferred<Unit>()
    private var botguardResponseReady = CompletableDeferred<String?>()
    private var mintResultReady = CompletableDeferred<String?>()

    /**
     * Get PO tokens for a video.
     * @param videoId The video ID (for content-bound token)
     * @param visitorData The visitor data (for session-bound token)
     * @return PoTokenResult with both tokens, or null if generation failed
     */
    suspend fun getTokens(videoId: String, visitorData: String): PoTokenResult? {
        // Check cache
        synchronized(lock) {
            val cached = perVideoTokens[videoId]
            val streaming = sessionStreamingToken
            if (cached != null && streaming != null) {
                return PoTokenResult(cached, streaming)
            }
        }

        // Ensure WebView is initialized and BotGuard challenge is complete
        val integrityToken = ensureIntegrityToken(visitorData) ?: return null

        // Mint per-video player token
        val playerToken = mintPoToken(integrityToken, videoId)
        if (playerToken == null) {
            Log.w(TAG, "Failed to mint player PO token for $videoId")
            return null
        }

        // Mint session streaming token (once per session)
        var streamingToken = sessionStreamingToken
        if (streamingToken == null) {
            streamingToken = mintPoToken(integrityToken, visitorData)
            if (streamingToken != null) {
                sessionStreamingToken = streamingToken
                Log.d(TAG, "Session streaming PO token acquired")
            }
        }

        if (streamingToken == null) {
            Log.w(TAG, "Failed to mint streaming PO token for $videoId")
            return null
        }

        val result = PoTokenResult(playerToken, streamingToken)
        synchronized(lock) {
            perVideoTokens[videoId] = playerToken
        }
        Log.d(TAG, "PO tokens minted for $videoId: player=${playerToken.take(20)}..., streaming=${streamingToken.take(20)}...")
        return result
    }

    /**
     * Ensure we have a valid session integrity token.
     */
    private suspend fun ensureIntegrityToken(visitorData: String): String? {
        // Check if we already have one
        sessionIntegrityToken?.let { return it }

        // Initialize WebView if needed
        ensureWebViewInitialized()

        // Fetch BotGuard challenge and run in WebView
        return initializeBotGuard(visitorData)
    }

    /**
     * Initialize the WebView with po_token.html and set up BotGuard.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebViewInitialized() {
        if (handlerThread != null && webView != null) return

        webViewReady = CompletableDeferred()
        botguardResponseReady = CompletableDeferred()
        mintResultReady = CompletableDeferred()

        handlerThread = HandlerThread("PoTokenWebView").apply { start() }
        handler = Handler(handlerThread!!.looper)

        // WebView MUST be created on the main thread
        val createLatch = CountDownLatch(1)
        var createdWebView: WebView? = null
        Handler(Looper.getMainLooper()).post {
            try {
                createdWebView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.safeBrowsingEnabled = false

                    // Add JavaScript interface for callbacks
                    addJavascriptInterface(PoTokenJsInterface(), JS_INTERFACE)

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            Log.d(TAG, "WebView page loaded: $url")
                            webViewReady.complete(Unit)
                        }

                        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                            Log.w(TAG, "WebView error: $errorCode $description at $failingUrl")
                            webViewReady.complete(Unit)
                        }
                    }

                    // Load the po_token.html asset
                    val html = context.assets.open("po_token.html").bufferedReader().readText()
                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebView creation failed: ${e.message}")
            } finally {
                createLatch.countDown()
            }
        }
        createLatch.await(5, TimeUnit.SECONDS)

        webView = createdWebView
        if (webView == null) {
            Log.e(TAG, "WebView creation timed out or failed")
            webViewReady.complete(Unit)
        }
    }

    /**
     * Initialize BotGuard by fetching challenge and running in WebView.
     */
    private suspend fun initializeBotGuard(visitorData: String): String? {
        // Wait for WebView to be ready
        webViewReady.await()

        // Fetch BotGuard challenge from Create endpoint
        val challengeData = fetchBotGuardChallenge() ?: run {
            Log.w(TAG, "Failed to fetch BotGuard challenge")
            return null
        }
        Log.d(TAG, "BotGuard challenge fetched: ${challengeData.toString().take(100)}...")

        // Run BotGuard in WebView
        val botguardResponse = runBotGuardInWebView(challengeData)
        if (botguardResponse == null) {
            Log.w(TAG, "BotGuard execution failed")
            return null
        }
        Log.d(TAG, "BotGuard response obtained (${botguardResponse.length} chars)")

        // Fetch integrity token from GenerateIT
        val integrityToken = fetchIntegrityToken(REQUEST_KEY, botguardResponse)
        if (integrityToken != null) {
            sessionIntegrityToken = integrityToken
            Log.d(TAG, "Session integrity token acquired (${integrityToken.length} chars)")
        }
        return integrityToken
    }

    /**
     * Fetch BotGuard challenge from YouTube Create endpoint.
     */
    private suspend fun fetchBotGuardChallenge(): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val body = """["O43z0dpjhgX20SCx4KAo"]"""

            val request = Request.Builder()
                .url(CREATE_URL)
                .post(body.toRequestBody("application/json+protobuf".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3")
                .header("Content-Type", "application/json+protobuf")
                .header("x-goog-api-key", GOOGLE_API_KEY)
                .header("x-user-agent", "grpc-web-javascript/0.1")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.w(TAG, "Create endpoint failed: ${response.code}")
                return@withContext null
            }

            // Response is a JSON array with challenge data
            val jsonArray = JsonParser.parseString(responseBody).asJsonArray
            if (jsonArray.size() < 2) {
                Log.w(TAG, "Unexpected Create response format")
                return@withContext null
            }

            // The challenge data is in the second element
            val challengeObj = jsonArray.get(1).asJsonObject
            challengeObj
        } catch (e: Exception) {
            Log.w(TAG, "fetchBotGuardChallenge failed: ${e.message}")
            null
        }
    }

    /**
     * Run BotGuard in WebView using the po_token.html asset.
     */
    private suspend fun runBotGuardInWebView(challengeData: JsonObject): String? {
        botguardResponseReady = CompletableDeferred()

        handler?.post {
            try {
                val wv = webView ?: run {
                    botguardResponseReady.complete(null)
                    return@post
                }

                // Convert challenge data to JSON string
                val challengeJson = Gson().toJson(challengeData)

                // Escape for JS injection
                val escapedChallenge = challengeJson
                    .replace("\\", "\\\\")
                    .replace("`", "\\`")
                    .replace("$", "\\$")

                // Call the downloadAndRunBotguard function in po_token.html
                val script = """
                    (function() {
                        try {
                            downloadAndRunBotguard($escapedChallenge);
                            return 'started';
                        } catch(e) {
                            return 'error:' + e.toString();
                        }
                    })()
                """.trimIndent()

                wv.evaluateJavascript(script) { result ->
                    Log.d(TAG, "BotGuard start result: $result")
                }
            } catch (e: Exception) {
                Log.w(TAG, "runBotGuardInWebView error: ${e.message}")
                botguardResponseReady.complete(null)
            }
        }

        // Wait for BotGuard response with timeout
        return withTimeoutOrNull(WEBVIEW_TIMEOUT_MS) {
            botguardResponseReady.await()
        }
    }

    /**
     * Fetch integrity token from GenerateIT endpoint.
     */
    private suspend fun fetchIntegrityToken(requestKey: String, botguardResponse: String): String? = withContext(Dispatchers.IO) {
        try {
            // Build the request body as JSON array
            val body = """["$requestKey", "$botguardResponse"]"""

            val request = Request.Builder()
                .url(GENERATE_IT_URL)
                .post(body.toRequestBody("application/json+protobuf".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3")
                .header("Content-Type", "application/json+protobuf")
                .header("x-goog-api-key", GOOGLE_API_KEY)
                .header("x-user-agent", "grpc-web-javascript/0.1")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.w(TAG, "GenerateIT failed: ${response.code}")
                return@withContext null
            }

            // Parse response array
            val jsonArray = JsonParser.parseString(responseBody).asJsonArray
            if (jsonArray.size() < 2) {
                Log.w(TAG, "Unexpected GenerateIT response format")
                return@withContext null
            }

            // The integrity token is in the second element
            val integrityToken = jsonArray.get(1).asString
            integrityToken
        } catch (e: Exception) {
            Log.w(TAG, "fetchIntegrityToken failed: ${e.message}")
            null
        }
    }

    /**
     * Mint a PO token using the minter function in the WebView.
     */
    private suspend fun mintPoToken(integrityToken: String, binding: String): String? {
        mintResultReady = CompletableDeferred()

        handler?.post {
            try {
                val wv = webView ?: run {
                    mintResultReady.complete(null)
                    return@post
                }

                // Store integrity token in WebView
                wv.evaluateJavascript("""
                    (function() {
                        setIntegrityToken('$integrityToken');
                    })()
                """.trimIndent(), null)

                // Convert binding to bytes and call obtainPoToken
                val bindingBytes = binding.toByteArray(Charsets.UTF_8)
                val bindingBase64 = Base64.encodeToString(bindingBytes, Base64.NO_WRAP)

                val script = """
                    (function() {
                        try {
                            // Decode base64 to string
                            var binding = atob('$bindingBase64');
                            obtainPoToken(binding).then(function(token) {
                                window._poTokenResult = token;
                            }).catch(function(e) {
                                window._poTokenError = e.toString();
                            });
                            return 'started';
                        } catch(e) {
                            return 'error:' + e.toString();
                        }
                    })()
                """.trimIndent()

                wv.evaluateJavascript(script) { result ->
                    Log.d(TAG, "Mint start result: $result")

                    // Poll for result
                    wv.postDelayed({
                        wv.evaluateJavascript("window._poTokenResult || window._poTokenError || 'pending'") { result2 ->
                            val cleanResult = result2?.removeSurrounding("\"")
                            if (cleanResult?.startsWith("error:") == true) {
                                Log.w(TAG, "Mint error: $cleanResult")
                                mintResultReady.complete(null)
                            } else if (cleanResult == "pending" || cleanResult.isNullOrBlank()) {
                                Log.w(TAG, "Mint still pending")
                                mintResultReady.complete(null)
                            } else {
                                mintResultReady.complete(cleanResult)
                            }
                        }
                    }, 1000)
                }
            } catch (e: Exception) {
                Log.w(TAG, "mintPoToken error: ${e.message}")
                mintResultReady.complete(null)
            }
        }

        return withTimeoutOrNull(MINT_TIMEOUT_MS) {
            mintResultReady.await()
        }
    }

    /**
     * Invalidate cached tokens (call on logout or session change).
     */
    fun invalidate() {
        synchronized(lock) {
            perVideoTokens.clear()
        }
        sessionIntegrityToken = null
        sessionStreamingToken = null
    }

    /**
     * Release WebView resources.
     */
    fun release() {
        handler?.post {
            webView?.destroy()
            webView = null
        }
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        invalidate()
    }

    // Keep extractPoTokenFromHtml for backward compatibility with fetchWatchPageFormats
    fun extractPoTokenFromHtml(html: String): String? {
        val patterns = listOf(
            Regex("""SERVICE_INTEGRITY_TOKEN["\s:]+poToken["\s:]+"([^"]+)""""),
            Regex(""""serviceIntegrityToken"\s*:\s*"([^"]+)""""),
            Regex(""""poToken"\s*:\s*"([^"]+)""""),
            Regex("""'poToken'\s*:\s*'([^']+)'""")
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // Keep getPoToken for backward compatibility
    suspend fun getPoToken(): String? {
        Log.w(TAG, "getPoToken() called (deprecated) — use getTokens() instead")
        return null
    }

    /**
     * JavaScript interface for WebView callbacks.
     */
    inner class PoTokenJsInterface {
        @JavascriptInterface
        fun onBotguardResponse(response: String) {
            Log.d(TAG, "BotGuard response received (${response.length} chars)")
            botguardResponseReady.complete(response)
        }

        @JavascriptInterface
        fun onBotguardError(error: String) {
            Log.w(TAG, "BotGuard error: $error")
            botguardResponseReady.complete(null)
        }

        @JavascriptInterface
        fun onPoTokenResult(token: String) {
            Log.d(TAG, "PO token result received (${token.length} chars)")
            mintResultReady.complete(token)
        }

        @JavascriptInterface
        fun onPoTokenError(error: String) {
            Log.w(TAG, "PO token error: $error")
            mintResultReady.complete(null)
        }
    }
}