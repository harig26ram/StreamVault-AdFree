package com.freedomplay.app.presentation.ui.screens.player

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.json.JSONObject

/**
 * Native-UI bridge into the YouTube web player. All methods run `#movie_player` API calls
 * (the same functions YouTube's own buttons trigger) via evaluateJavascript, so the app's
 * Compose UI stays the single, uniform control surface.
 */
class HdPlayerController {
    data class PlayerState(
        val position: Double,
        val duration: Double,
        val playing: Boolean
    )

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

    fun getState(onResult: (PlayerState) -> Unit) {
        playerCallWithResult(
            "var pos=0,dur=0,playing=false;" +
            "if(p&&p.getCurrentTime)pos=p.getCurrentTime();else if(v)pos=v.currentTime;" +
            "if(p&&p.getDuration)dur=p.getDuration();else if(v)dur=v.duration;" +
            "if(p&&p.getPlayerState)playing=p.getPlayerState()===1;else if(v)playing=!v.paused;" +
            "return JSON.stringify({p:pos,d:dur,a:playing});",
        ) { result ->
            try {
                val json = JSONObject(result)
                onResult(
                    PlayerState(
                        position = json.getDouble("p"),
                        duration = json.getDouble("d"),
                        playing = json.getBoolean("a")
                    )
                )
            } catch (_: Exception) {
                // Fallback to individual calls if batched parse fails
                getCurrentPosition { pos ->
                    getDuration { dur ->
                        isPlaying { playing ->
                            onResult(PlayerState(pos, dur, playing))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberHdPlayerController(): HdPlayerController = remember { HdPlayerController() }