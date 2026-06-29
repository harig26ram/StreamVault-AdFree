package com.streamvault.app.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.streamvault.app.adblocking.AdBlocker
import com.streamvault.app.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    url: String,
    modifier: Modifier = Modifier,
    adBlockingEnabled: Boolean = true,
    sponsorBlockEnabled: Boolean = true,
    backgroundPlayEnabled: Boolean = true,
    onProgressChanged: ((Int) -> Unit)? = null,
    onPageStarted: ((String?) -> Unit)? = null,
    onPageFinished: ((String?) -> Unit)? = null,
    webViewRef: ((WebView) -> Unit)? = null
) {
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // Handle file chooser result - no-op for now since YouTube doesn't need file upload
    }

    DisposableEffect(Unit) {
        onDispose {
            // WebView cleanup handled by parent
        }
    }

    if (loadError != null) {
        WebViewErrorState(
            error = loadError!!,
            onRetry = {
                loadError = null
                isLoading = true
            }
        )
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                    setSupportMultipleWindows(false)
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (AdBlocker.shouldBlockRequest(request, adBlockingEnabled)) {
                            return AdBlocker.getAdBlockResponse()
                        }
                        return null
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        isLoading = true
                        loadError = null
                        onPageStarted?.invoke(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoading = false
                        onPageFinished?.invoke(url)
                        view?.evaluateJavascript(AdBlocker.AD_BLOCK_JS, null)
                        if (sponsorBlockEnabled) {
                            view?.evaluateJavascript(AdBlocker.SPONSORBLOCK_JS, null)
                        }
                        if (backgroundPlayEnabled) {
                            view?.evaluateJavascript(AdBlocker.BACKGROUND_PLAY_JS, null)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val errorCode = error?.errorCode ?: -1
                            loadError = when (errorCode) {
                                -1 -> "Connection failed"
                                -2 -> "Connection timed out"
                                -6 -> "Page not found"
                                -8 -> "Connection interrupted"
                                -11 -> "SSL error"
                                else -> "Failed to load (error $errorCode)"
                            }
                            isLoading = false
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        handler?.cancel()
                        loadError = "SSL certificate error"
                        isLoading = false
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("intent://") || url.startsWith("market://")) {
                            return true
                        }
                        return false
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged?.invoke(newProgress)
                    }
                }

                webViewRef?.invoke(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            webViewRef?.invoke(webView)
        }
    )
}

@Composable
fun WebViewErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent
                )
            ) {
                Text("Try Again", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun WebViewLoadingState(
    progress: Int
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                progress = progress / 100f,
                color = Accent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading... $progress%",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}
