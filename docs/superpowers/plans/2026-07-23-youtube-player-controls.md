# YouTube-like Player Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add YouTube-like controls (seek bar, play/pause, skip ±10s, auto-hide, double-tap skip, fullscreen) to the HD WebView player path.

**Architecture:** Extend `HdPlayerController` with JS-based position/duration/isPlaying getters, then wire them into a new `HdPlayerControls` composable on `PlayerScreen.kt`. Double-tap overlay sits above controls but beneath `TouchLockOverlay`. Fullscreen toggles system bars, orientation, and layout.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, WebView (JS bridge), Coroutines

## Global Constraints

- Package: `com.freedomplay.app`
- Min SDK 24, Target SDK 36
- AMOLED theme: pure black (#000000) backgrounds, hot pink (#FF4081) accents
- Use `Icons.AutoMirrored.Filled.*` for deprecated Compose icons
- Follow existing patterns in `TouchLockOverlay.kt` for gesture detection
- All durations formatted via `TimeUtils.formatDuration(seconds: Long): String`
- `Stream` model has `duration: Long?` field (seconds)

---

### Task 1: Add JS getters to HdPlayerController

**Files:**
- Modify: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdWebPlayer.kt`

**Interfaces:**
- Produces: `HdPlayerController.getCurrentPosition(onResult: (Double) -> Unit)`, `getDuration(onResult: (Double) -> Unit)`, `isPlaying(onResult: (Boolean) -> Unit)`

- [ ] **Step 1: Add `evaluateJavascript` with callback helper**

Add a private helper that wraps `evaluateJavascript` with a callback:

```kotlin
private fun execWithResult(js: String, onResult: (String) -> Unit) {
    val wv = webView ?: return
    wv.post { wv.evaluateJavascript(js) { value -> onResult(value ?: "") } }
}
```

- [ ] **Step 2: Add getter methods**

Add these methods to `HdPlayerController`:

```kotlin
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
```

Add a `playerCallWithResult` method (like `playerCall` but returns result):

```kotlin
private fun playerCallWithResult(body: String, onResult: (String) -> Unit) = execWithResult(
    "(function(){var p=document.getElementById('movie_player')||document.querySelector('.html5-video-player');var v=document.querySelector('video');$body})()",
    onResult
)
```

- [ ] **Step 3: Build and verify**

```bash
.\gradlew.bat assembleDebug
```

Verify the file compiles successfully — the new methods won't be called until wired in later tasks.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdWebPlayer.kt
git commit -m "feat(player): add JS-based position/duration/isPlaying getters to HdPlayerController"
```

---

### Task 2: Create HdPlayerControls composable

**Files:**
- Create: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdPlayerControls.kt`

**Interfaces:**
- Consumes: `HdPlayerController` (from Task 1), `TimeUtils.formatDuration()`
- Produces: `HdPlayerControls(controller, duration, onToggleFullscreen)` composable
- Used by: `PlayerScreen.kt` (Task 4)

- [ ] **Step 1: Create the file with polling coroutine and state**

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplay.app.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HdPlayerControls(
    controller: HdPlayerController,
    duration: Long?,
    isPlaying: Boolean,
    currentPosition: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val durationSeconds = duration?.toFloat() ?: 0f
    val sliderValue = if (isDragging) sliderPosition else currentPosition
    val elapsedFormatted = TimeUtils.formatDuration((sliderValue / 1000f).toLong())
    val durationFormatted = TimeUtils.formatDuration((durationSeconds / 1000f).toLong())

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Seek bar row with time labels
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
                        value = if (durationSeconds > 0f)
                            (sliderValue / durationSeconds).coerceIn(0f, 1f) else 0f,
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

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons row
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
                }

                // Fullscreen button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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
}
```

- [ ] **Step 2: Build and verify**

```bash
.\gradlew.bat assembleDebug
```

Verify the new file compiles. It won't be called yet but should compile as a standalone composable.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/HdPlayerControls.kt
git commit -m "feat(player): add HdPlayerControls composable with seek bar, play/pause, skip, fullscreen"
```

---

### Task 3: Create DoubleTapSkipOverlay composable

**Files:**
- Create: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/DoubleTapSkipOverlay.kt`

**Interfaces:**
- Consumes: `onSkipForward: () -> Unit`, `onSkipBackward: () -> Unit`
- Produces: `DoubleTapSkipOverlay(onSkipForward, onSkipBackward, modifier)` composable

- [ ] **Step 1: Create the double-tap composable**

```kotlin
package com.freedomplay.app.presentation.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val SKIP_INDICATOR_DURATION_MS = 500L

@Composable
fun DoubleTapSkipOverlay(
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    modifier: Modifier = Modifier
) {
    var skipDirection by remember { mutableStateOf<String?>(null) }
    var skipKey by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current

    LaunchedEffect(skipKey) {
        if (skipDirection != null) {
            delay(SKIP_INDICATOR_DURATION_MS)
            skipDirection = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val middleX = size.width / 2f
                        if (offset.x < middleX) {
                            skipDirection = "backward"
                            onSkipBackward()
                        } else {
                            skipDirection = "forward"
                            onSkipForward()
                        }
                        skipKey = System.currentTimeMillis()
                    }
                )
            }
    ) {
        if (skipDirection != null) {
            val alpha by animateFloatAsState(
                targetValue = if (skipDirection != null) 1f else 0f,
                animationSpec = tween(300),
                label = "skipAlpha"
            )
            Box(
                modifier = Modifier
                    .align(
                        if (skipDirection == "backward") Alignment.CenterStart
                        else Alignment.CenterEnd
                    )
                    .padding(24.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (skipDirection == "backward")
                        Icons.Default.Replay10 else Icons.Default.Forward10,
                    contentDescription = "Skip ${skipDirection?.replace("ward", "")} 10s",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            // Fade out after showing
            LaunchedEffect(skipDirection) {
                delay(SKIP_INDICATOR_DURATION_MS)
                skipDirection = null
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
.\gradlew.bat assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/DoubleTapSkipOverlay.kt
git commit -m "feat(player): add double-tap skip overlay with visual indicators"
```

---

### Task 4: Wire controls + fullscreen into PlayerScreen

**Files:**
- Modify: `app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/PlayerScreen.kt`

**Interfaces:**
- Consumes: `HdPlayerControls`, `DoubleTapSkipOverlay`, `HdPlayerController` getters (Task 1)
- Depends on: Task 1, Task 2, Task 3

- [ ] **Step 1: Add imports for new composables and fullscreen APIs**

Add these imports to `PlayerScreen.kt`:

```kotlin
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.WindowInsets as ComposeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
```

- [ ] **Step 2: Add state variables for controls**

After `val hdController = rememberHdPlayerController()` (line ~119), add:

```kotlin
var controlsVisible by remember { mutableStateOf(true) }
var isFullscreen by remember { mutableStateOf(false) }
var hdPosition by remember { mutableFloatStateOf(0f) }
var hdIsPlaying by remember { mutableStateOf(true) }
```

- [ ] **Step 3: Add auto-hide LaunchedEffect**

After the PiP `DisposableEffect` (line ~130), add:

```kotlin
LaunchedEffect(controlsVisible) {
    if (controlsVisible) {
        delay(3000)
        controlsVisible = false
    }
}
```

- [ ] **Step 4: Add polling LaunchedEffect for position/playing state**

```kotlin
LaunchedEffect(currentVideoId) {
    while (isActive) {
        hdController.getCurrentPosition { pos ->
            hdPosition = (pos * 1000f).coerceAtLeast(0f)
        }
        hdController.isPlaying { playing ->
            hdIsPlaying = playing
        }
        delay(500)
    }
}
```

- [ ] **Step 5: Add fullscreen toggle logic**

Add before or after the polling LaunchedEffect:

```kotlin
fun toggleFullscreen() {
    val act = context as? ComponentActivity ?: return
    isFullscreen = !isFullscreen
    if (isFullscreen) {
        act.window.insetsController?.hide(
            WindowInsets.Type.systemBars()
        )
        act.window.insetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        act.window.insetsController?.show(
            WindowInsets.Type.systemBars()
        )
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
```

- [ ] **Step 6: Replace the HD player Box with controls + overlay**

Replace lines 191-205 with:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .then(
            if (isFullscreen) Modifier.fillMaxSize()
            else Modifier.aspectRatio(16f / 9f)
        )
        .background(Color.Black)
) {
    key(currentVideoId) {
        HdWebPlayer(
            videoId = currentVideoId,
            modifier = Modifier.fillMaxSize(),
            controller = hdController
        )
    }

    // Tap to toggle controls (not consumed by double-tap overlay)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible }
                )
            }
    )

    // Double-tap skip overlay (only handles double-tap, passes through single taps)
    DoubleTapSkipOverlay(
        onSkipForward = {
            hdController.getCurrentPosition { pos ->
                hdController.seekTo((pos + 10).coerceAtMost(99999.0))
            }
        },
        onSkipBackward = {
            hdController.getCurrentPosition { pos ->
                hdController.seekTo((pos - 10).coerceAtLeast(0.0))
            }
        }
    )

    // Player controls overlay
    HdPlayerControls(
        controller = hdController,
        duration = currentStream.duration,
        isPlaying = hdIsPlaying,
        currentPosition = hdPosition,
        onTogglePlay = {
            if (hdIsPlaying) hdController.pause() else hdController.play()
        },
        onSeek = { posMillis ->
            hdController.seekTo((posMillis / 1000f).toDouble())
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
        visible = controlsVisible
    )

    // Restore controls visibility on any interaction
    LaunchedEffect(hdPosition) {
        // Position changes don't reset auto-hide timer
    }
}
```

Note: This requires these additional imports:

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
```

- [ ] **Step 7: Handle fullscreen layout for the LazyColumn**

Replace line 207 `if (!isPiPActive) {` with:

```kotlin
if (!isPiPActive && !isFullscreen) {
```

- [ ] **Step 8: Add display cutout padding in fullscreen**

For display cutout awareness, add inside the `HdPlayerControls` call or wrap the controls Box with:

```kotlin
val cutoutPadding = if (isFullscreen) {
    Modifier.padding(
        top = with(LocalDensity.current) {
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        }
    )
} else Modifier
```

(This can be simplified — `WindowInsets.displayCutout.asPaddingValues()` provides the cutout insets directly.)

- [ ] **Step 9: Build and verify**

```bash
.\gradlew.bat assembleDebug
```

Fix any compilation errors.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/freedomplay/app/presentation/ui/screens/player/PlayerScreen.kt
git commit -m "feat(player): wire HD player controls, double-tap skip, and fullscreen into PlayerScreen"
```

---

### Task 5: Emulator QA

**Files:** No code changes.

- [ ] **Step 1: Install and launch on emulator**

```bash
.\gradlew.bat installDebug
```

- [ ] **Step 2: Manual QA checklist**

1. Launch a video in HD mode — verify controls appear on screen
2. Tap play/pause — verify video pauses/resumes
3. Drag seek bar — verify video seeks to position
4. Tap forward/backward 10s — verify video skips
5. Wait 3s — verify controls auto-hide
6. Tap video — verify controls reappear
7. Double-tap left half — verify backward skip indicator + video seeks back
8. Double-tap right half — verify forward skip indicator + video seeks forward
9. Tap fullscreen button — verify system bars hide, orientation changes to landscape
10. Tap fullscreen button again — verify system bars restore, orientation goes back
11. Verify TouchLockOverlay still works above controls
12. Verify PiP still works

- [ ] **Step 3: Fix any bugs found during QA**

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix(player): address QA findings for HD player controls"
```
