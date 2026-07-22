package com.freedomplay.app.data.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import com.freedomplay.app.BuildConfig
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.NewPipe
import java.time.Instant

// Ported from NewPipe (GPLv3). Adapted to use the app's registered NewPipe Downloader instead of
// NewPipe app's DownloaderImpl, and to drop the optional androidx.webkit SafeBrowsing tweak.
class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    private val generatorEmitter: SingleEmitter<PoTokenGenerator>
) : PoTokenGenerator {
    private val webView = WebView(context)
    private val disposables = CompositeDisposable() // used only during initialization
    private val poTokenEmitters = mutableListOf<Pair<String, SingleEmitter<String>>>()
    private lateinit var expirationInstant: Instant

    //region Initialization
    init {
        val webViewSettings = webView.settings
        //noinspection SetJavaScriptEnabled we want to use JavaScript!
        webViewSettings.javaScriptEnabled = true
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    val fmt = "\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})"
                    val exception = BadWebViewException(fmt)
                    Log.e(TAG, "This WebView implementation is broken: $fmt")

                    onInitializationErrorCloseAndCancel(exception)
                    popAllPoTokenEmitters().forEach { (_, emitter) -> emitter.onError(exception) }
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    private fun loadHtmlAndObtainBotguard(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadHtmlAndObtainBotguard() called")
        }

        disposables.add(
            Single.fromCallable {
                context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { html ->
                        webView.loadDataWithBaseURL(
                            "https://www.youtube.com",
                            html.replaceFirst(
                                "</script>",
                                "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                            ),
                            "text/html",
                            "utf-8",
                            null
                        )
                    },
                    this::onInitializationErrorCloseAndCancel
                )
        )
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "downloadAndRunBotguard() called")
        }

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]"
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Initialization error from JavaScript: $error")
        }
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "botguardResponse: $botguardResponse")
        }
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]"
        ) { responseBody ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "GenerateIT response: $responseBody")
            }
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)

            // leave 10 minutes of margin just to be sure
            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)

            webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "initialization finished, expiration=${expirationTimeInSeconds}s")
                }
                generatorEmitter.onSuccess(this)
            }
        }
    }
    //endregion

    //region Obtaining poTokens
    override fun generatePoToken(identifier: String): Single<String> = Single.create { emitter ->
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "generatePoToken() called with identifier $identifier")
        }
        runOnMainThread(emitter) {
            addPoTokenEmitter(identifier, emitter)
            val u8Identifier = stringToU8(identifier)
            webView.evaluateJavascript(
                """try {
                        identifier = "$identifier"
                        u8Identifier = $u8Identifier
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        poTokenU8String = ""
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ","
                            poTokenU8String += poTokenU8[i]
                        }
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }"""
            ) {}
        }
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        }
        popPoTokenEmitter(identifier)?.onError(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken (before decoding): identifier=$identifier poTokenU8=$poTokenU8")
        }
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            popPoTokenEmitter(identifier)?.onError(t)
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        }
        popPoTokenEmitter(identifier)?.onSuccess(poToken)
    }

    override fun isExpired(): Boolean {
        return Instant.now().isAfter(expirationInstant)
    }
    //endregion

    //region Handling multiple emitters
    private fun addPoTokenEmitter(identifier: String, emitter: SingleEmitter<String>) {
        synchronized(poTokenEmitters) {
            poTokenEmitters.add(Pair(identifier, emitter))
        }
    }

    private fun popPoTokenEmitter(identifier: String): SingleEmitter<String>? {
        return synchronized(poTokenEmitters) {
            poTokenEmitters.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let {
                poTokenEmitters.removeAt(it).second
            }
        }
    }

    private fun popAllPoTokenEmitters(): List<Pair<String, SingleEmitter<String>>> {
        return synchronized(poTokenEmitters) {
            val result = poTokenEmitters.toList()
            poTokenEmitters.clear()
            result
        }
    }
    //endregion

    //region Utils
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit
    ) {
        disposables.add(
            Single.fromCallable {
                NewPipe.getDownloader().post(
                    url,
                    mapOf(
                        "User-Agent" to listOf(USER_AGENT),
                        "Accept" to listOf("application/json"),
                        "Content-Type" to listOf("application/json+protobuf"),
                        "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                        "x-user-agent" to listOf("grpc-web-javascript/0.1")
                    ),
                    data.toByteArray()
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { response ->
                        val httpCode = response.responseCode()
                        if (httpCode != 200) {
                            onInitializationErrorCloseAndCancel(
                                PoTokenException("Invalid response code: $httpCode")
                            )
                            return@subscribe
                        }
                        handleResponseBody(response.responseBody())
                    },
                    this::onInitializationErrorCloseAndCancel
                )
        )
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        runOnMainThread(generatorEmitter) {
            close()
            generatorEmitter.onError(error)
        }
    }

    @MainThread
    override fun close() {
        disposables.dispose()

        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }
    //endregion

    companion object : PoTokenGenerator.Factory {
        private val TAG = PoTokenWebView::class.simpleName

        // Public API key used by BotGuard, obtained by looking at BotGuard requests.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw" // NOSONAR
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        override fun newPoTokenGenerator(context: Context): Single<PoTokenGenerator> =
            Single.create { emitter ->
                runOnMainThread(emitter) {
                    val potWv = PoTokenWebView(context, emitter)
                    potWv.loadHtmlAndObtainBotguard(context)
                    emitter.setDisposable(potWv.disposables)
                }
            }

        private fun runOnMainThread(
            emitterIfPostFails: SingleEmitter<out Any>,
            runnable: Runnable
        ) {
            if (!Handler(Looper.getMainLooper()).post(runnable)) {
                emitterIfPostFails.onError(PoTokenException("Could not run on main thread"))
            }
        }
    }
}
