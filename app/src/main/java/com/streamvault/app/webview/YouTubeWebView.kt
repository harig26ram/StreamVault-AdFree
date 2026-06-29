package com.streamvault.app.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.streamvault.app.adblocking.AdBlocker

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    url: String,
    modifier: Modifier = Modifier,
    onProgressChanged: ((Int) -> Unit)? = null,
    onPageStarted: ((String?) -> Unit)? = null,
    onPageFinished: ((String?) -> Unit)? = null,
    enableBackgroundPlay: Boolean = true,
    enableSponsorBlock: Boolean = true,
    webViewRef: ((WebView) -> Unit)? = null
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
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
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (AdBlocker.shouldBlockRequest(request)) {
                            return AdBlocker.getAdBlockResponse()
                        }
                        return null
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted?.invoke(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished?.invoke(url)
                        view?.evaluateJavascript(AdBlocker.AD_BLOCK_JS, null)
                        if (enableSponsorBlock) {
                            view?.evaluateJavascript(AdBlocker.SPONSORBLOCK_JS, null)
                        }
                        if (enableBackgroundPlay) {
                            view?.evaluateJavascript(AdBlocker.BACKGROUND_PLAY_JS, null)
                        }
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

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        return true
                    }
                }

                webView = this
                webViewRef?.invoke(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            webViewRef?.invoke(webView)
        }
    )
}
