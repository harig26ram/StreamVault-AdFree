# Universal Player Module — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace overlay controls with a persistent bottom player bar (YouTube parity) — seek slider + time labels + play/pause + skip ±10s + fullscreen + Max HD. Fix lag via hardware-accelerated WebView + batched JS calls. Auto-hide after 3s inactivity.

**Architecture:** Minimal WebView (only `<video>` element visible) + native Compose `BottomPlayerControls` bar. `HdPlayerController` JS bridge provides playback state. 500ms poll updates Compose state. Gesture routing handles taps/double-taps without conflicts.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, WebView JS bridge, `WindowInsetsController` fullscreen

## Global Constraints

- Min SDK 24, Target SDK 36
- Package: `com.freedomplay.app`
- Kotlin, Compose, Material 3 conventions
- AMOLED theme: pure black backgrounds, hot pink (#FF4081) accents
- Deprecated Compose icons use `Icons.AutoMirrored.Filled.*`
- `TimeUtils.formatDuration(seconds: Long)` takes seconds
- `HdPlayerController.getCurrentPosition` returns `Double` (seconds)
- `HdPlayerController.seekTo` takes `Double` (seconds)

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `MinimalWebPlayer.kt` | CREATE | WebView wrapper: loads YouTube, injects CSS to hide chrome, hardware accel |
| `BottomPlayerControls.kt` | CREATE | Persistent bottom bar: seek slider, time labels, play/pause, skip, FS, quality |
| `HdPlayerController.kt` | MODIFY | Add `getDuration()`, `getState()` batched call |
| `PlayerScreen.kt` | REFACTOR | Replace `HdWebPlayer`+`HdPlayerControls` with `MinimalWebPlayer`+`BottomPlayerControls` |
| `HdPlayerControls.kt` | DELETE | Replaced by `BottomPlayerControls` |
| `HdWebPlayer.kt` | DELETE | Replaced by `MinimalWebPlayer` |

---

### Task 1: Enhance `HdPlayerController` — Add `getDuration` and batched `getState`

**Files:**
- Modify: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdWebPlayer.kt:66-89`

**Interfaces:**
- Consumes: existing `playerCallWithResult`, `execWithResult`
- Produces: `getDuration(onResult: (Double) -> Unit)`, `getState(onResult: (Triple<Double, Double, Boolean>) -> Unit)`

- [ ] **Step 1: Add `getDuration` to `HdPlayerController`**

In `HdWebPlayer.kt`, add this method to `HdPlayerController` after `getCurrentPosition`:

```kotlin
fun getDuration(onResult: (Double) -> Unit) {
    playerCallWithResult(
        "var d=0;if(p&&p.getDuration)d=p.getDuration();else if(v)d=v.duration;return d;",
    ) { result ->
        onResult(result.toDoubleOrNull() ?: 0.0)
    }
}
```

- [ ] **Step 2: Add batched `getState` to `HdPlayerController`**

Add this method after `getDuration`:

```kotlin
data class PlayerState(val position: Double, val duration: Double, val playing: Boolean)

fun getState(onResult: (PlayerState) -> Unit) {
    playerCallWithResult(
        """var pos=0,dur=0,paused=true;
        if(p&&p.getCurrentTime)pos=p.getCurrentTime();
        else if(v)pos=v.currentTime;
        if(p&&p.getDuration)dur=p.getDuration();
        else if(v)dur=v.duration;
        if(p&&p.getPlayerState)paused=p.getPlayerState()!==1;
        else if(v)paused=v.paused;
        return JSON.stringify({p:pos,d:dur,a:!paused});""",
    ) { result ->
        try {
            val json = result.removeSurrounding("\"").replace("\\\"", "\"")
            val p = json.indexOf("\"p\":").takeIf { it >= 0 }?.let { json.substring(it + 4, json.indexOf(",", it)).toDoubleOrNull() } ?: 0.0
            val d = json.indexOf("\"d\":").takeIf { it >= 0 }?.let { json.substring(it + 4, json.indexOf(",", it)).toDoubleOrNull() } ?: 0.0
            val a = json.indexOf("\"a\":").takeIf { it >= 0 }?.let { json.substring(it + 4, json.indexOf("}", it)).toBooleanStrictOrNull() } ?: false
            onResult(PlayerState(p, d, a))
        } catch (_: Exception) {
            onResult(PlayerState(0.0, 0.0, false))
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdWebPlayer.kt
git commit -m "feat(player): add getDuration and batched getState to HdPlayerController"
```

---

### Task 2: Create `MinimalWebPlayer.kt`

**Files:**
- Create: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/MinimalWebPlayer.kt`

**Interfaces:**
- Consumes: `HdPlayerController` (existing)
- Produces: `MinimalWebPlayer(videoId, modifier, controller)` composable

- [ ] **Step 1: Create `MinimalWebPlayer.kt`**

```kotlin
package com.freedomplay.app.presentation.ui.screens.player

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Minimal WebView that loads YouTube's mobile watch page and strips ALL chrome
 * via CSS injection — only the <video> element is visible. The app's native
 * Compose controls handle all user interaction.
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
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
                }
                // Hardware acceleration for smoother playback
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                // Fullscreen support via WebChromeClient
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
                    }

                    override fun onHideCustomView() {
                        val activity = ctx.findActivity() ?: return
                        customView?.let {
                            (activity.window.decorView as? FrameLayout)?.removeView(it)
                        }
                        customView = null
                        callback?.onCustomViewHidden()
                        callback = null
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(MINIMAL_PLAYER_CSS, null)
                        cookieManager.flush()
                    }
                }

                // Disable touch on WebView — Compose handles all gestures
                setOnTouchListener { _, _ -> true }

                controller?.webView = this
                loadUrl("https://m.youtube.com/watch?v=$videoId")
            }
        },
        update = { /* videoId changes go through controller.loadVideo() */ },
        onRelease = { webView ->
            if (controller?.webView === webView) controller.webView = null
            CookieManager.getInstance().flush()
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.destroy()
        }
    )
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * CSS injected after page load to hide ALL YouTube chrome and show only <video>.
 */
private const val MINIMAL_PLAYER_CSS = """
(function(){
  var st = document.createElement('style');
  st.id = 'fp-minimal-player';
  st.textContent = [
    'html, body { overflow: hidden !important; height: 100% !important; background: #000 !important; margin: 0 !important; }',
    '#movie_player, .html5-video-player, .html5-video-container, video {',
    '  width: 100% !important; height: 100% !important;',
    '  position: fixed !important; top: 0 !important; left: 0 !important;',
    '  z-index: 99999 !important; background: #000 !important;',
    '}',
    'ytm-mobile-topbar-renderer, .mobile-topbar-header, ytm-masthead, header,',
    'ytm-pivot-bar-renderer, ytm-single-column-watch-next-results-renderer,',
    'ytm-comment-section-renderer, ytm-comments-entry-point-teaser-renderer,',
    'ytm-companion-slot, ytm-promoted-sparkles-web-renderer,',
    '.ytp-paid-content-overlay, .ytp-ce-element,',
    '.ytp-chrome-bottom, .ytp-chrome-top, .ytp-gradient-bottom, .ytp-gradient-top,',
    '.ytp-watermark, .ytp-title, .ytp-fullscreen-button, .ytp-settings-button,',
    '.ytp-play-button, .ytp-time-display, .ytp-progress-bar-container,',
    '.ytp-chrome-controls, .html5-video-info-panel,',
    '#related, #comments, #meta, #secondary, #masthead,',
    'ytd-app, ytd-page-manager, ytd-watch-flexy {',
    '  display: none !important; visibility: hidden !important;',
    '  opacity: 0 !important; pointer-events: none !important;',
    '}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(st);

  // Auto-unmute and play
  var v = document.querySelector('video');
  if (v) {
    if (v.muted) v.muted = false;
    if (v.paused) v.play().catch(function(){});
  }

  // Watchdog: re-apply CSS if YouTube SPA re-renders (every 10s)
  if (!window.__fpMinimalWatchdog) {
    window.__fpMinimalWatchdog = setInterval(function(){
      var existing = document.getElementById('fp-minimal-player');
      if (!existing) {
        (document.head || document.documentElement).appendChild(st.cloneNode(true));
      }
      var vid = document.querySelector('video');
      if (vid && vid.muted) vid.muted = false;
    }, 10000);
  }
})();
"""
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/MinimalWebPlayer.kt
git commit -m "feat(player): add MinimalWebPlayer — WebView with hidden YouTube chrome"
```

---

### Task 3: Create `BottomPlayerControls.kt`

**Files:**
- Create: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/BottomPlayerControls.kt`

**Interfaces:**
- Consumes: `TimeUtils.formatDuration(seconds: Long)` (existing)
- Produces: `BottomPlayerControls` composable

- [ ] **Step 1: Create `BottomPlayerControls.kt`**

```kotlin
package com.freedomplay.app.presentation.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplay.app.util.TimeUtils

/**
 * Persistent bottom player controls — YouTube parity.
 * Always visible (or auto-hidden via [visible]), shows seek bar + time + playback controls.
 */
@Composable
fun BottomPlayerControls(
    duration: Float,
    currentPosition: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onMaxQuality: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val durationSeconds = if (duration > 0f) duration else 1f
    val sliderValue = if (isDragging) sliderPosition else currentPosition
    val elapsedFormatted = TimeUtils.formatDuration(sliderValue.toLong())
    val durationFormatted = TimeUtils.formatDuration(durationSeconds.toLong())

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Seek bar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = elapsedFormatted,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = (sliderValue / durationSeconds).coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        isDragging = true
                        sliderPosition = fraction * durationSeconds
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek(sliderPosition)
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = durationFormatted,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipBackward) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "Backward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = onSkipForward) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onMaxQuality) {
                    Icon(
                        Icons.Default.HighQuality,
                        contentDescription = "Max HD",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/BottomPlayerControls.kt
git commit -m "feat(player): add BottomPlayerControls — persistent seek bar, time labels, playback controls"
```

---

### Task 4: Refactor `PlayerScreen.kt` — Wire new components + touch routing

**Files:**
- Modify: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/PlayerScreen.kt`

**Interfaces:**
- Consumes: `MinimalWebPlayer`, `BottomPlayerControls`, `HdPlayerController.PlayerState`
- Produces: Updated `PlayerScreen` composable

- [ ] **Step 1: Update imports and state variables**

Replace the old imports and add new ones. The key changes in `PlayerScreen.kt`:

```kotlin
// REMOVE these imports:
// import com.freedomplay.app.presentation.ui.screens.player.HdPlayerControls (no longer used in PlayerScreen)

// ADD these imports:
import com.freedomplay.app.presentation.ui.screens.player.MinimalWebPlayer
import com.freedomplay.app.presentation.ui.screens.player.BottomPlayerControls
```

Replace the state variables block (around lines 121-129):

```kotlin
var isPiPActive by remember { mutableStateOf(false) }
var isDescriptionExpanded by remember { mutableStateOf(false) }
var isTouchLocked by remember { mutableStateOf(false) }
var showDownloadDialog by remember { mutableStateOf(false) }
val hdController = rememberHdPlayerController()
var controlsVisible by remember { mutableStateOf(true) }
var isFullscreen by remember { mutableStateOf(false) }
var hdPosition by remember { mutableFloatStateOf(0f) }
var hdDuration by remember { mutableFloatStateOf(0f) }
var hdIsPlaying by remember { mutableStateOf(true) }
```

- [ ] **Step 2: Replace the 500ms polling LaunchedEffect**

Replace the existing `LaunchedEffect(currentVideoId)` polling block (lines 149-159) with:

```kotlin
// Batched poll: single JS call returns position + duration + playing state
LaunchedEffect(currentVideoId) {
    while (isActive) {
        hdController.getState { state ->
            hdPosition = state.position.toFloat().coerceAtLeast(0f)
            hdDuration = state.duration.toFloat().coerceAtLeast(0f)
            hdIsPlaying = state.playing
        }
        delay(500)
    }
}
```

- [ ] **Step 3: Replace HdWebPlayer with MinimalWebPlayer**

In the HD mode Box (around line 248), replace:

```kotlin
key(currentVideoId) {
    HdWebPlayer(
        videoId = currentVideoId,
        modifier = Modifier.fillMaxSize(),
        controller = hdController
    )
}
```

With:

```kotlin
key(currentVideoId) {
    MinimalWebPlayer(
        videoId = currentVideoId,
        modifier = Modifier.fillMaxSize(),
        controller = hdController
    )
}
```

- [ ] **Step 4: Replace HdPlayerControls with BottomPlayerControls**

Remove the old `HdPlayerControls` call (lines 282-304) and replace with:

```kotlin
// Bottom player controls (persistent, auto-hide)
BottomPlayerControls(
    duration = hdDuration,
    currentPosition = hdPosition,
    isPlaying = hdIsPlaying,
    onTogglePlay = {
        if (hdIsPlaying) hdController.pause() else hdController.play()
    },
    onSeek = { posSeconds ->
        hdController.seekTo(posSeconds.toDouble())
    },
    onSkipForward = {
        hdController.getCurrentPosition { pos ->
            hdController.seekTo((pos + 10).coerceAtMost(99999.0))
        }
    },
    onSkipBackward = {
        hdController.getCurrentPosition { pos ->
            hdController.seekTo((pos - 10).coerceAtLeast(0.0))
        }
    },
    onToggleFullscreen = { toggleFullscreen() },
    onMaxQuality = { hdController.setMaxQuality() },
    visible = controlsVisible,
    modifier = Modifier.align(Alignment.BottomCenter)
)
```

- [ ] **Step 5: Fix the tap gesture Box z-order**

The tap gesture Box must be ABOVE the video but BELOW `BottomPlayerControls`. Replace the tap gesture Box (lines 257-265) with:

```kotlin
// Tap to toggle controls — must be ABOVE video but BELOW BottomPlayerControls
// DoubleTapSkipOverlay handles double-taps separately
Box(
    modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { controlsVisible = !controlsVisible }
            )
        }
)
```

- [ ] **Step 6: Ensure BottomPlayerControls is at correct z-order**

The `BottomPlayerControls` must be ABOVE the tap gesture Box. In the parent Box, add a vertical Arrangement or use explicit z-ordering. The current structure should work since `BottomPlayerControls` is added after the tap gesture Box in the composable tree.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/PlayerScreen.kt
git commit -m "refactor(player): wire MinimalWebPlayer + BottomPlayerControls, fix tap gesture z-order"
```

---

### Task 5: Delete old files + build + test

**Files:**
- Delete: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdPlayerControls.kt`
- Delete: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdWebPlayer.kt`

- [ ] **Step 1: Delete old files**

```bash
git rm app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdPlayerControls.kt
git rm app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdWebPlayer.kt
```

- [ ] **Step 2: Build**

```bash
.\gradlew.bat assembleDebug
```

- [ ] **Step 3: Fix any compilation errors**

If build fails, read the error messages and fix import mismatches, missing methods, or type errors.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(player): delete old HdPlayerControls and HdWebPlayer, replaced by MinimalWebPlayer + BottomPlayerControls"
```

---

### Task 6: Install on emulator + QA

- [ ] **Step 1: Install on emulator**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Launch and test**

```bash
adb shell am start -n com.freedomplay.app/.MainActivity
```

- [ ] **Step 3: QA Checklist**

| # | Test | Expected | Pass? |
|---|------|----------|-------|
| 1 | Open a video | Persistent seek bar visible at bottom of player | |
| 2 | Time labels | Show `current / duration` (e.g., `1:23 / 10:45`) | |
| 3 | Drag seek bar | Video jumps to dragged position | |
| 4 | Play/Pause button | Toggles video playback | |
| 5 | Skip ±10s buttons | Seeks forward/backward 10 seconds | |
| 6 | Double-tap left | Seeks backward 10s with animated indicator | |
| 7 | Double-tap right | Seeks forward 10s with animated indicator | |
| 8 | Wait 3s | Controls fade out automatically | |
| 9 | Tap video | Controls fade back in | |
| 10 | Fullscreen button | Rotates to landscape, hides system bars | |
| 11 | Max HD button | Forces highest quality | |
| 12 | Lock button | Disables all gestures | |
| 13 | Unlock button | Re-enables gestures | |
| 14 | Like button | Toggles favorite state | |
| 15 | Share button | Opens share chooser | |
| 16 | Download button | Shows quality dialog | |
| 17 | Back button | Returns to previous screen | |
| 18 | Mini player | Persists after back with Pause/Close | |
| 19 | Video swap (related tap) | New video loads in same WebView, no flash | |
| 20 | No lag | Smooth 60fps scrub, no stutter on play/pause | |

- [ ] **Step 4: Fix any issues found in QA**

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat(player): universal player module — persistent controls bar, YouTube-parity UX, lag fix"
```
