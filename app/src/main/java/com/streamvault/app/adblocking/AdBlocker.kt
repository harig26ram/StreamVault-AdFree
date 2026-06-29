package com.streamvault.app.adblocking

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

object AdBlocker {

    private val adDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "ad.doubleclick.net", "ad.turn.com", "ads.yahoo.com", "adnxs.com",
        "adsrvr.org", "amazon-adsystem.com", "criteo.com", "criteo.net",
        "moatads.com", "permutive.com", "rubiconproject.com", "taboola.com",
        "outbrain.com", "pubmatic.com", "sharethrough.com", "simpli.fi",
        "spotxchange.com", "tidaltv.com", "yieldmo.com", "zeustech.com",
        "ads.youtube.com", "ytsafxbling.com", "syndication.videoads",
        "static.doubleclick.net", "s0.2mdn.net", "s0.2mdn.net",
        "video-ad-stats.googleusercontent.com", "adeventtracker.com",
        "quantserve.com", "scorecardresearch.com", "bluekai.com",
        "demdex.net", "everesttech.net", "crwdcntrl.net",
        "rlcdn.com", "liadm.com", "mathtag.com",
        "bidswitch.net", "contextweb.com", "mediaplex.com",
        "serving-sys.com", "smaato.net", "inmobi.com",
        "unityads.unity3d.com", "adcolony.com", "applovin.com",
        "vungle.com", "fyber.com", "startapp.com",
        "chartboost.com", "heyzap.com", "supersonicads.com"
    )

    private val adUrlPatterns = listOf(
        "/ads/", "/advert/", "/advertisement/",
        "pagead", "googlesyndication", "googleads",
        "/sponsor", "/promo/", "banner_ad",
        "videoads", "preroll", "midroll", "postroll",
        "ima3.", "googletagmanager"
    )

    fun shouldBlockRequest(request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val domain = try {
            java.net.URL(url).host.lowercase()
        } catch (e: Exception) {
            url.lowercase()
        }

        if (adDomains.any { domain.contains(it) }) return true
        if (adUrlPatterns.any { url.lowercase().contains(it) }) return true

        return false
    }

    fun getAdBlockResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
    }

    const val AD_BLOCK_JS = """
        (function() {
            const style = document.createElement('style');
            style.textContent = `
                [id*="ad"], [class*="ad"], [id*="banner"], [class*="banner"],
                [id*="sponsor"], [class*="sponsor"], [id*="promo"], [class*="promo"],
                .ytp-ad-overlay-container, .ytp-ad-text-overlay,
                .ytp-ad-image-overlay, .ytp-ad-survey,
                .video-ads, .ad-container, .ad-slot, .ad-unit,
                [data-ad], [data-ad-slot], [data-ad-unit],
                .ytp-ad-player-overlay, .ytp-ad-module,
                #player-overlay-ads, #ad-container,
                .ytd-banner-promo-renderer, .ytd-promoted-sparkles-web-renderer,
                .ytd-display-ad-renderer, .ytd-video-masthead-ad-renderer,
                .ytd-ad-slot-renderer, .ytd-in-feed-ad-layout-renderer,
                .ytd-statement-banner-renderer-ad,
                ytd-ad-slot-renderer, ytd-promoted-video-renderer
            {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                max-height: 0 !important;
                overflow: hidden !important;
                position: absolute !important;
                left: -9999px !important;
            }
            `;
            document.head.appendChild(style);

            function skipAds() {
                var adVideo = document.querySelector('.video-ads video');
                if (adVideo && adVideo.duration) {
                    adVideo.muted = true;
                    adVideo.currentTime = adVideo.duration - 0.1;
                }
                var adShowing = document.querySelector('.ad-showing');
                if (adShowing) {
                    var mainVideo = document.querySelector('.html5-main-video');
                    if (mainVideo && mainVideo.duration) {
                        mainVideo.muted = true;
                        mainVideo.currentTime = mainVideo.duration - 0.1;
                    }
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button.ytp-ad-skip-button');
                    if (skipBtn) skipBtn.click();
                }
                var adOverlay = document.querySelector('.ytp-ad-overlay-close-button');
                if (adOverlay) adOverlay.click();
            }

            setInterval(skipAds, 100);

            var observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    mutation.addedNodes.forEach(function(node) {
                        if (node.nodeType === 1) {
                            if (node.id && (node.id.includes('ad') || node.id.includes('sponsor'))) {
                                node.style.display = 'none';
                            }
                            if (node.className && typeof node.className === 'string') {
                                if (node.className.includes('ad-') || node.className.includes('ad_') ||
                                    node.className.includes('sponsor') || node.className.includes('promo')) {
                                    node.style.display = 'none';
                                }
                            }
                        }
                    });
                });
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
        })();
    """

    const val BACKGROUND_PLAY_JS = """
        (function() {
            var style = document.createElement('style');
            style.textContent = `
                video {
                    -webkit-media-controls: true !important;
                }
            `;
            document.head.appendChild(style);
        })();
    """

    const val SPONSORBLOCK_JS = """
        (function() {
            function getVideoId() {
                var url = window.location.href;
                var match = url.match(/[?&]v=([^&]+)/);
                if (match) return match[1];
                var pathMatch = url.match(/youtu\.be\/([^?]+)/);
                if (pathMatch) return pathMatch[1];
                return null;
            }

            function skipSponsorSegments(video) {
                var videoId = getVideoId();
                if (!videoId || !window._sponsorblockData) return;

                var segments = window._sponsorblockData[videoId];
                if (!segments) return;

                var currentTime = video.currentTime;
                for (var i = 0; i < segments.length; i++) {
                    var seg = segments[i];
                    if (currentTime >= seg.start && currentTime < seg.end - 0.1) {
                        video.currentTime = seg.end;
                        break;
                    }
                }
            }

            setInterval(function() {
                var video = document.querySelector('video');
                if (video) {
                    skipSponsorSegments(video);
                }
            }, 500);
        })();
    """
}
