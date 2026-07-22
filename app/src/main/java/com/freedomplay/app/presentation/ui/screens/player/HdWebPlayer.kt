package com.freedomplay.app.presentation.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * Native-UI bridge into the YouTube web player. All methods run `#movie_player` API calls
 * (the same functions YouTube's own buttons trigger) via evaluateJavascript, so the app's
 * Compose UI stays the single, uniform control surface.
 */
class HdPlayerController {
    internal var webView: WebView? = null

    private fun exec(js: String) {
        val wv = webView ?: return
        wv.post { wv.evaluateJavascript(js, null) }
    }

    private fun playerCall(body: String) = exec(
        "(function(){var p=document.getElementById('movie_player')||document.querySelector('.html5-video-player');var v=document.querySelector('video');$body})()"
    )

    fun play() = playerCall("if(p&&p.playVideo){p.playVideo();}else if(v){v.play();}")
    fun pause() = playerCall("if(p&&p.pauseVideo){p.pauseVideo();}else if(v){v.pause();}")
    fun seekTo(seconds: Double) = playerCall("if(p&&p.seekTo){p.seekTo($seconds,true);}else if(v){v.currentTime=$seconds;}")
    fun setMaxQuality() = playerCall(
        "if(p&&p.getAvailableQualityLevels){var l=p.getAvailableQualityLevels();" +
        "if(l&&l.length){if(p.setPlaybackQualityRange)p.setPlaybackQualityRange(l[0],l[0]);" +
        "if(p.setPlaybackQuality)p.setPlaybackQuality(l[0]);}}"
    )

    /** Switch to another video without recreating the WebView (keeps cookies/session warm). */
    fun loadVideo(videoId: String) {
        val wv = webView ?: return
        wv.post { wv.loadUrl("https://m.youtube.com/watch?v=$videoId") }
    }

    private fun execWithResult(js: String, onResult: (String) -> Unit) {
        val wv = webView ?: return
        wv.post { wv.evaluateJavascript(js) { value -> onResult(value ?: "") } }
    }

    private fun playerCallWithResult(body: String, onResult: (String) -> Unit) = execWithResult(
        "(function(){var p=document.getElementById('movie_player')||document.querySelector('.html5-video-player');var v=document.querySelector('video');$body})()",
        onResult
    )

    fun getCurrentPosition(onResult: (Double) -> Unit) {
        playerCallWithResult(
            "var t=0;if(p&&p.getCurrentTime)t=p.getCurrentTime();else if(v)t=v.currentTime;return t;",
        ) { result ->
            onResult(result.toDoubleOrNull() ?: 0.0)
        }
    }

    fun getDuration(onResult: (Double) -> Unit) {
        playerCallWithResult(
            "var d=0;if(p&&p.getDuration)d=p.getDuration();else if(v)d=v.duration;return d;",
        ) { result ->
            onResult(result.toDoubleOrNull() ?: 0.0)
        }
    }

    fun isPlaying(onResult: (Boolean) -> Unit) {
        playerCallWithResult(
            "var r=false;if(p&&p.getPlayerState)r=p.getPlayerState()===1;else if(v)r=!v.paused;return r;",
        ) { result ->
            onResult(result == "true")
        }
    }
}

@Composable
fun rememberHdPlayerController(): HdPlayerController = remember { HdPlayerController() }

/**
 * HD playback via YouTube's own player inside a WebView, loading the full mobile watch page
 * (`m.youtube.com/watch`). YouTube's player streams full adaptive HD itself (it handles
 * signatures/poToken/SABR internally), which extractor-based playback cannot do anonymously.
 *
 * The WebView is a PURE PLAYER SURFACE: injected CSS pins `#movie_player` to fill the viewport
 * and hides the watch page's own chrome/feed/comments, and page scrolling is disabled. This
 * removes YouTube's in-feed promoted items and the "video floating over the feed" z-order
 * glitch — the app renders its own (ad-filtered) related list natively below this composable.
 *
 * - Network ads/trackers are stripped by intercepting known ad hosts.
 * - In-player ads are auto-skipped (skip button clicked / ad fast-forwarded) by the watchdog JS.
 * - Quality is forced to the highest available; the user can still use YouTube's own gear menu.
 * - Cookies persist (CookieManager), so the Settings sign-in keeps the session personalized.
 * - Fullscreen is handled by promoting the player's custom view to the Activity decor.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HdWebPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    controller: HdPlayerController? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // Persist cookies so a one-time sign-in on the watch page keeps the session
            // personalized/ad-free on later loads.
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            WebView(ctx).apply {
                cookieManager.setAcceptThirdPartyCookies(this, true)

                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptCanOpenWindowsAutomatically = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Real mobile-Chrome UA so YouTube serves its normal HD mobile web player.
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
                }
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                // Fullscreen: promote YouTube's fullscreen video view onto the Activity's decor.
                webChromeClient = object : WebChromeClient() {
                    private var customView: View? = null
                    private var callback: CustomViewCallback? = null

                    override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                        val activity = ctx.findActivity() ?: return
                        customView = view
                        callback = cb
                        (activity.window.decorView as? FrameLayout)?.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        activity.requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }

                    override fun onHideCustomView() {
                        val activity = ctx.findActivity() ?: return
                        (activity.window.decorView as? FrameLayout)?.removeView(customView)
                        customView = null
                        callback?.onCustomViewHidden()
                        callback = null
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString()?.lowercase() ?: return null
                        if (AD_HOSTS.any { url.contains(it) }) {
                            return BLOCKED
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(PLAYER_ONLY_JS, null)
                        cookieManager.flush()
                    }
                }

                controller?.webView = this
                loadUrl("https://m.youtube.com/watch?v=$videoId")
            }
        },
        update = { /* videoId changes go through controller.loadVideo / key(videoId) in caller */ },
        onRelease = { webView ->
            if (controller?.webView === webView) controller.webView = null
            CookieManager.getInstance().flush()
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.destroy()
        }
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// Injected after the watch page loads and re-run by a watchdog interval (the <video> and player
// chrome appear asynchronously, and YouTube re-mutes/re-renders during SPA updates):
//  1. CSS: pin #movie_player to fill the WebView viewport, hide all page chrome/feed, kill scroll.
//  2. Unmute + autoplay.
//  3. Force highest available quality.
//  4. Auto-skip in-player ads (click skip button; fast-forward unskippable ones).
private const val PLAYER_ONLY_JS = """
(function(){
  var cssApplied = false, adState = false, ready = false, ticks = 0;
  function ensureCss(){
    if (cssApplied && document.getElementById('fp-player-only')) return;
    var st = document.createElement('style');
    st.id = 'fp-player-only';
    st.textContent = [
      'html, body { overflow: hidden !important; height: 100% !important; background: #000 !important; }',
      '#movie_player, .html5-video-player {',
      '  position: fixed !important; top: 0 !important; left: 0 !important;',
      '  width: 100vw !important; height: 100vh !important; z-index: 99999 !important;',
      '  background: #000 !important;',
      '}',
      'ytm-mobile-topbar-renderer, .mobile-topbar-header, ytm-masthead, header,',
      'ytm-pivot-bar-renderer, ytm-single-column-watch-next-results-renderer,',
      'ytm-comment-section-renderer, ytm-comments-entry-point-teaser-renderer,',
      'ytm-companion-slot, ytm-promoted-sparkles-web-renderer,',
      '.ytp-paid-content-overlay, .ytp-ce-element { display: none !important; }'
    ].join('\n');
    (document.head || document.documentElement).appendChild(st);
    cssApplied = true;
  }
  function fix(){
    try {
      ticks++;
      if (!cssApplied || !document.getElementById('fp-player-only')) cssApplied = false;
      ensureCss();
      var v = document.querySelector('video');
      var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
      // Auto-skip in-player ads
      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
      if (skip) { skip.click(); }
      var isAd = p && p.classList && p.classList.contains('ad-showing');
      if (isAd && v && isFinite(v.duration) && v.duration > 0) {
        v.currentTime = v.duration;
      }
      // Unmute + play (only check muted state on ad transitions or every 10th tick)
      if (v && (isAd !== adState || ticks % 10 === 0)) {
        if (v.muted) { v.muted = false; }
        if (v.paused) { v.play().catch(function(){}); }
      }
      var unmute = document.querySelector('button[aria-label*="nmute"], [class*="unmute" i]');
      if (unmute) { unmute.click(); }
      // Force quality only on state transitions
      if (isAd !== adState || !ready) {
        adState = isAd;
        if (p && !isAd) {
          try {
            var levels = p.getAvailableQualityLevels ? p.getAvailableQualityLevels() : null;
            var best = (levels && levels.length) ? levels[0] : 'highres';
            if (p.setPlaybackQualityRange) { p.setPlaybackQualityRange(best, best); }
            if (p.setPlaybackQuality) { p.setPlaybackQuality(best); }
          } catch (e2) {}
        }
        ready = true;
      }
      if (window.scrollY !== 0 || window.scrollX !== 0) { window.scrollTo(0, 0); }
    } catch (e) {}
  }
  fix();
  // Throttled watchdog (5s): catches mid-roll ads; reduced from 1.5s to avoid DOM thrash
  // on emulators with software-rendered WebViews.
  if (!window.__fpWatchdog) {
    window.__fpWatchdog = setInterval(fix, 5000);
  }
})();
"""

// Ad / tracking hosts and paths to block. The player streams normally without these.
private val AD_HOSTS = listOf(
    "doubleclick.net",
    "googleadservices.com",
    "googlesyndication.com",
    "google-analytics.com",
    "/pagead/",
    "/pagead",
    "/ptracking",
    "/api/stats/ads",
    "/api/stats/qoe",
    "/get_midroll_",
    "/api/stats/atr",
    "adservice.google",
    "/csi_204",
    "/generate_204",
    "video_masthead"
)

private val BLOCKED: WebResourceResponse
    get() = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )
