# Design: YouTube-like Player Controls for HD WebView

> Date: 2026-07-23 · Status: Draft

## Problem

The HD WebView player (`HdWebPlayer`) hides YouTube's native controls via CSS injection and provides zero native controls — no seek bar, no duration display, no skip buttons, no play/pause, no fullscreen. The `HdPlayerController` already has `play()`, `pause()`, `seekTo()` JS bridge methods but they are not wired to any UI.

## Goal

Add YouTube-like player controls to the HD WebView path:
- Timeline seek bar with elapsed/total duration labels
- Play/pause button
- Forward/backward 10s skip buttons
- Auto-hide controls after inactivity (3s)
- Double-tap to skip (YouTube mobile style)
- Fullscreen mode with landscape, system bar hiding, and display cutout awareness

## Architecture

### HdPlayerController Extensions

Add position/duration getters via YouTube's `#movie_player` JS API:

```kotlin
fun getCurrentPosition(onResult: (Double) -> Unit) {
    playerCall("var t=0;if(p&&p.getCurrentTime)t=p.getCurrentTime();else if(v)t=v.currentTime;return t;")
    // Evaluate with callback → parse result → call onResult(seconds)
}

fun getDuration(onResult: (Double) -> Unit) {
    playerCall("var d=0;if(p&&p.getDuration)d=p.getDuration();else if(v)d=v.duration;return d;")
    // Same pattern
}

fun isPlaying(onResult: (Boolean) -> Unit) {
    playerCall("var r=false;if(p&&p.getPlayerState)r=p.getPlayerState()===1;else if(v)r=!v.paused;return r;")
}
```

The composable polls these every 500ms via coroutine and stores values in `mutableFloatStateOf` / `mutableStateOf<Boolean>`.

### New Composable: `HdPlayerControls`

A single composable that renders all player controls for the HD path:

```
┌──────────────────────────────────────┐
│           WebView (16:9)             │
├──────────────────────────────────────┤
│ ──●──────────────────── 1:23 / 5:42  │  ← Material 3 Slider + time labels
│    ◀◀ 10s    ▶⏸ Play/Pause    10s ▶▶ │  ← Skip buttons + play/pause
│                                  [⛶] │  ← Fullscreen toggle
├──────────────────────────────────────┤
│ Title, actions, description...       │
```

Props: `controller: HdPlayerController`, `duration: Long?` (from Stream model), `onToggleFullscreen: () -> Unit`.

### Auto-hide Controls

- `controlsVisible: Boolean` state, initially `true`
- `LaunchedEffect` with `delay(3000)` hides controls
- Any tap on the video area resets: sets `controlsVisible = true`, restarts timer
- Controls fade in/out via `AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut())`
- Controls area has semi-transparent black background (`Color.Black.copy(alpha = 0.7f)`) for readability

### Double-tap to Skip

Custom gesture detector on the video area using `PointerInputScope`:

- Tracks tap count within 300ms window
- **Left half tap** → backward 10s
- **Right half tap** → forward 10s
- Visual feedback: animated circle with `«10` or `10»` text, fades after 500ms
- Does NOT consume taps that aren't double-taps → WebView still receives single taps for play/pause

### Fullscreen Mode

**Toggle:** A fullscreen icon (`Icons.Default.Fullscreen`) in the controls bar.

**When entering fullscreen:**
1. Hide system bars via `WindowInsetsController.hide(WindowInsetsCompat.Type.systemBars())`
2. Set `WindowInsetsController.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
3. Force landscape via `activity.requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`
4. Set `isFullscreen = true` → video Box expands to `Modifier.fillMaxSize()`, LazyColumn hidden
5. Controls overlay on top of video with display cutout-aware padding

**Display cutout handling:**
```kotlin
val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
// Apply as top padding to controls in fullscreen mode
// Ensures controls aren't behind notch/camera
```

**When exiting fullscreen:**
1. Restore system bars via `WindowInsetsController.show()`
2. Restore orientation via `activity.requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED`
3. Set `isFullscreen = false` → back to 16:9 aspect ratio, LazyColumn visible

**Layout in fullscreen:**
```
┌──────────────────────────────────────┐
│ ▬▬▬ display cutout padding ▬▬▬▬▬▬▬▬  │  ← WindowInsets.displayCutout.top
├──────────────────────────────────────┤
│                                      │
│         Video (fills screen)         │
│                                      │
├──────────────────────────────────────┤
│ ──●──────────────────── 1:23 / 5:42  │  ← Controls (bottom)
│    ◀◀ 10s    ▶⏸ Play/Pause    01s ▶▶ │
│                                  [⛶] │
└──────────────────────────────────────┘
```

## Files to Change

| File | Changes |
|------|---------|
| `HdWebPlayer.kt` | Add `getCurrentPosition()`, `getDuration()`, `isPlaying()` to `HdPlayerController` |
| `PlayerScreen.kt` | Add `HdPlayerControls()` composable, `DoubleTapSkipOverlay()`, fullscreen state/logic, restructure HD layout, wire controls to controller |

## Interaction Model

| User Action | Result |
|-------------|--------|
| Tap video | Toggle controls visibility |
| Double-tap left half | Skip backward 10s (visual indicator) |
| Double-tap right half | Skip forward 10s (visual indicator) |
| Drag seek bar | Seek to position |
| Tap play/pause | Toggle playback |
| Tap forward/backward 10s | Skip ±10s |
| Tap fullscreen button | Enter/exit fullscreen |
| 3s inactivity | Controls auto-hide |
| Swipe from edge (fullscreen) | System bars reappear temporarily |

## Not in Scope

- Double-tap hold to fast-forward (like YouTube's 2x speed on hold)
- Long-press to seek scrub
- Chapter markers on timeline
- Swipe volume/brightness gestures
