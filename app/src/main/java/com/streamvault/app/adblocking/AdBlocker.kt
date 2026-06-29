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
        "ads.youtube.com", "syndication.videoads",
        "static.doubleclick.net", "s0.2mdn.net",
        "video-ad-stats.googleusercontent.com", "adeventtracker.com",
        "quantserve.com", "scorecardresearch.com", "bluekai.com",
        "demdex.net", "everesttech.net", "crwdcntrl.net",
        "rlcdn.com", "liadm.com", "mathtag.com",
        "bidswitch.net", "contextweb.com", "mediaplex.com",
        "serving-sys.com", "smaato.net", "inmobi.com",
        "unity3d.com", "adcolony.com", "applovin.com",
        "vungle.com", "fyber.com", "startapp.com",
        "chartboost.com", "heyzap.com", "supersonicads.com",
        "googleadservices.com", "pagead2.googlesyndication.com",
        "ad.lgappstv.com", "ads.api.33across.com",
        "adsatt.espn.com", "advertising.com",
        "cdn.doubleverify.com", "cdn.taboola.com",
        "gscontxt.net", "iasds01.com",
        "insurads.com", "mookie1.com",
        "nxtck.com", "onescreen.com",
        "openx.net", "optmd.com",
        "sas.com", "serving-sys.com",
        "smartadserver.com", "spotxchange.com",
        "stickyadstv.com", "tribalfusion.com",
        "turn.com", "videologygroup.com"
    )

    private val adUrlPatterns = listOf(
        "/ads/", "/advert/", "/advertisement/",
        "pagead", "googlesyndication", "googleads",
        "banner_ad", "videoads",
        "preroll", "midroll", "postroll",
        "ima3.", "googletagmanager",
        "/advertising/", "/adsense/",
        "ad_break", "adunit", "ad-slot"
    )

    fun shouldBlockRequest(request: WebResourceRequest?, adBlockingEnabled: Boolean = true): Boolean {
        if (!adBlockingEnabled) return false
        val url = request?.url?.toString() ?: return false
        val domain = try {
            java.net.URL(url).host.lowercase()
        } catch (e: Exception) {
            url.lowercase()
        }

        if (adDomains.any { domain == it || domain.endsWith(".$it") }) return true
        if (adUrlPatterns.any { url.lowercase().contains(it) }) return true

        return false
    }

    fun getAdBlockResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
    }

    const val AD_BLOCK_JS = """
        (function() {
            if (window.__sv_adblock_injected) return;
            window.__sv_adblock_injected = true;

            var style = document.createElement('style');
            style.setAttribute('data-sv-adblock', 'true');
            style.textContent = [
                '[id*="ad"]:not(video):not(button)',
                '[class*="ad-"]:not(.add):not(.address):not(.admin):not(.adult)',
                '[class*="ad_"]:not(.address)',
                '[id*="banner"]',
                '[class*="banner"]',
                '[id*="sponsor"]',
                '[class*="sponsor"]',
                '[id*="promo"]:not(.promoted)',
                '[class*="promo"]:not(.promoted)',
                '.ytp-ad-overlay-container',
                '.ytp-ad-text-overlay',
                '.ytp-ad-image-overlay',
                '.ytp-ad-survey',
                '.video-ads',
                '.ad-container',
                '.ad-slot',
                '.ad-unit',
                '[data-ad]',
                '[data-ad-slot]',
                '.ytp-ad-player-overlay',
                '.ytp-ad-module',
                '#player-overlay-ads',
                '#ad-container',
                '.ytd-banner-promo-renderer',
                '.ytd-promoted-sparkles-web-renderer',
                '.ytd-display-ad-renderer',
                '.ytd-video-masthead-ad-renderer',
                '.ytd-ad-slot-renderer',
                '.ytd-in-feed-ad-layout-renderer',
                '.ytd-statement-banner-renderer-ad',
                'ytd-ad-slot-renderer',
                'ytd-promoted-video-renderer',
                '.ytd-primetime-promo-renderer'
            ].join(',') + ' { display:none!important; visibility:hidden!important; height:0!important; max-height:0!important; overflow:hidden!important; position:absolute!important; left:-9999px!important; }';
            document.head.appendChild(style);

            var skipCount = 0;
            function skipAds() {
                try {
                    var adVideo = document.querySelector('.video-ads video');
                    if (adVideo && adVideo.duration && isFinite(adVideo.duration)) {
                        adVideo.muted = true;
                        adVideo.currentTime = adVideo.duration - 0.1;
                        skipCount++;
                    }
                    var adShowing = document.querySelector('.ad-showing');
                    if (adShowing) {
                        var mainVideo = document.querySelector('.html5-main-video');
                        if (mainVideo && mainVideo.duration && isFinite(mainVideo.duration)) {
                            mainVideo.muted = true;
                            mainVideo.currentTime = mainVideo.duration - 0.1;
                        }
                        var skipBtns = document.querySelectorAll(
                            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                            'button.ytp-ad-skip-button, .ytp-ad-skip-button-modern-slot, ' +
                            '.ytp-skip-ad-button-modern, .video-ad-skip-button'
                        );
                        skipBtns.forEach(function(btn) { btn.click(); });
                    }
                    var adOverlay = document.querySelector('.ytp-ad-overlay-close-button');
                    if (adOverlay) adOverlay.click();
                } catch(e) {}
            }

            setInterval(skipAds, 500);

            var observer = new MutationObserver(function(mutations) {
                var shouldSkip = false;
                for (var i = 0; i < mutations.length; i++) {
                    var nodes = mutations[i].addedNodes;
                    for (var j = 0; j < nodes.length; j++) {
                        var node = nodes[j];
                        if (node.nodeType !== 1) continue;
                        var id = node.id || '';
                        var cls = (typeof node.className === 'string') ? node.className : '';
                        if (id.match(/ad|sponsor|promo|banner/i) || cls.match(/ad-|ad_|sponsor|promo/i)) {
                            node.style.display = 'none';
                            shouldSkip = true;
                        }
                    }
                }
                if (shouldSkip) skipAds();
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
        })();
    """

    const val BACKGROUND_PLAY_JS = """
        (function() {
            if (window.__sv_bgplay_injected) return;
            window.__sv_bgplay_injected = true;

            var style = document.createElement('style');
            style.textContent = 'video { -webkit-media-controls: allow-playback-by-remote-airplay!important; -webkit-media-controls-allow-inline-playback!important; }';
            document.head.appendChild(style);

            document.addEventListener('visibilitychange', function() {
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    if (!v.paused && document.hidden) {
                        v.play().catch(function(){});
                    }
                });
            });
        })();
    """

    const val SPONSORBLOCK_JS = """
        (function() {
            if (window.__sv_sb_injected) return;
            window.__sv_sb_injected = true;

            function getVideoId() {
                var url = window.location.href;
                var match = url.match(/[?&]v=([^&]+)/);
                if (match) return match[1];
                var pathMatch = url.match(/youtu\\.be\\/([^?]+)/);
                if (pathMatch) return pathMatch[1];
                return null;
            }

            function fetchSegments(videoId) {
                if (window.__sv_sb_cache && window.__sv_sb_cache[videoId]) return;
                fetch('https://sponsor.ajay.app/api/skipSegments?videoID=' + videoId + '&categories=%5B%22sponsor%22%2C%22selfpromo%22%2C%22interaction%22%2C%22intro%22%2C%22outro%22%2C%22preview%22%2C%22music_offtopic%22%5D')
                    .then(function(r) { return r.json(); })
                    .then(function(segments) {
                        if (!window.__sv_sb_cache) window.__sv_sb_cache = {};
                        window.__sv_sb_cache[videoId] = segments.map(function(s) {
                            return { start: s.segment[0], end: s.segment[1], category: s.category };
                        });
                    })
                    .catch(function() {});
            }

            function skipSponsorSegments(video) {
                var videoId = getVideoId();
                if (!videoId) return;
                fetchSegments(videoId);
                var cache = window.__sv_sb_cache;
                if (!cache || !cache[videoId]) return;
                var segments = cache[videoId];
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
                if (video && !video.paused) {
                    skipSponsorSegments(video);
                }
            }, 1000);
        })();
    """
}
