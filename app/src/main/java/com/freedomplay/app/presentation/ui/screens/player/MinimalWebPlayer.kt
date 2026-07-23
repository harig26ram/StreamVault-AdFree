package com.freedomplay.app.presentation.ui.screens.player

import android.annotation.SuppressLint
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * WebView that loads YouTube's mobile watch page and strips ALL YouTube chrome via CSS
 * injection — only the `<video>` element is visible. Compose provides all gestures/controls.
 *
 * Reuses the existing [HdPlayerController] so video swaps don't recreate the WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MinimalWebPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    controller: HdPlayerController? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
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
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
                }
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString()?.lowercase() ?: return null
                        if (MINIMAL_AD_HOSTS.any { url.contains(it) }) {
                            return MINIMAL_BLOCKED
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(MINIMAL_PLAYER_JS, null)
                        cookieManager.flush()
                    }
                }

                // Disable all touch — Compose handles gestures via overlays
                setOnTouchListener { _, _ -> true }

                controller?.webView = this
                loadUrl("https://m.youtube.com/watch?v=$videoId")
            }
        },
        update = { /* videoId changes go through controller.loadVideo */ },
        onRelease = { webView ->
            if (controller?.webView === webView) controller.webView = null
            CookieManager.getInstance().flush()
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.destroy()
        }
    )
}

/**
 * CSS + JS injected after the watch page loads and re-run by a 10s watchdog interval.
 *
 * 1. CSS: pin player to fill viewport, hide ALL YouTube chrome (topbar, pivot bar,
 *    comments, related, ad overlays, play button, time display, progress bar, settings,
 *    fullscreen button, any other controls).
 * 2. Unmute + autoplay.
 * 3. Auto-skip in-player ads.
 * 4. Scroll lock.
 */
private const val MINIMAL_PLAYER_JS = """
(function(){
  var cssApplied = false, adState = false, ready = false, ticks = 0;
  function ensureCss(){
    if (cssApplied && document.getElementById('fp-minimal-css')) return;
    var st = document.createElement('style');
    st.id = 'fp-minimal-css';
    st.textContent = [
      'html, body { overflow: hidden !important; height: 100% !important; background: #000 !important; }',

      '#movie_player, .html5-video-player, video {',
      '  position: fixed !important; top: 0 !important; left: 0 !important;',
      '  width: 100vw !important; height: 100vh !important;',
      '  z-index: 99999 !important; background: #000 !important;',
      '  object-fit: contain !important;',
      '}',

      // Hide ALL YouTube chrome — topbar, pivot bar, comments, related
      'ytm-mobile-topbar-renderer, .mobile-topbar-header, ytm-masthead, header,',
      'ytm-pivot-bar-renderer, ytm-single-column-watch-next-results-renderer,',
      'ytm-comment-section-renderer, ytm-comments-entry-point-teaser-renderer,',
      'ytm-engagement-panel-section-list-renderer, ytm-comments-renderer,',
      '#contents.ytm-single-column-watch-next-results-renderer,',

      // Hide ad overlays and paid content
      'ytm-companion-slot, ytm-promoted-sparkles-web-renderer,',
      '.ytp-paid-content-overlay, .ytp-ce-element,',
      '.ytp-ad-overlay-container, .ytp-ad-text-overlay,',

      // Hide YouTube player controls — play button, time, progress, settings, fullscreen
      '.ytp-chrome-top, .ytp-chrome-bottom,',
      '.ytp-gradient-top, .ytp-gradient-bottom,',
      '.ytp-pause-overlay, .ytp-spinner,',
      '.ytp-cued-thumbnail-overlay,',
      '.ytp-bezel, .ytp-bezel-icon-wrapper,',
      '.ytp-watermark, .ytp-show-cards-title,',

      // Hide related videos, recommendations, end screens
      '.ytp-endscreen-content, .ytp-cards-teaser,',
      'ytm-item-section-renderer,',

      // Hide anything else floating over the video
      '.ytp-ce-covering-overlay, .ytp-ce-element { display: none !important; }'
    ].join('\n');
    (document.head || document.documentElement).appendChild(st);
    cssApplied = true;
  }
  function fix(){
    try {
      ticks++;
      if (!cssApplied || !document.getElementById('fp-minimal-css')) cssApplied = false;
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

      // Unmute + autoplay (check on ad transitions or every 10th tick)
      if (v && (isAd !== adState || ticks % 10 === 0)) {
        if (v.muted) { v.muted = false; }
        if (v.paused) { v.play().catch(function(){}); }
      }
      var unmute = document.querySelector('button[aria-label*="nmute"], [class*="unmute" i]');
      if (unmute) { unmute.click(); }

      // Force highest quality on state transitions
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
  // 10s watchdog: catches mid-roll ads and SPA re-renders
  if (!window.__fpMinimalWatchdog) {
    window.__fpMinimalWatchdog = setInterval(fix, 10000);
  }
})();
"""

private val MINIMAL_AD_HOSTS = listOf(
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

private val MINIMAL_BLOCKED: WebResourceResponse
    get() = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )
