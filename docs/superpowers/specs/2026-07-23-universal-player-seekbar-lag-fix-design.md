# Universal Player Module: Seek Bar, YouTube-Parity Controls, Lag Fix

**Date:** 2026-07-23  
**Branch:** `dev`  
**Status:** DRAFT — pending user review

---

## Context

- **Task 4** (wire HD controls) and **Task 5** (emulator QA) completed. Commit `15e81e9`.
- Current player: `HdWebPlayer` (WebView loading `m.youtube.com/watch`) + `HdPlayerControls` (overlay, auto-hides after 3s).
- **User pain points in emulator (`emulator-5554`, 1080×2424):**
  1. Seek bar / duration bar not visible (controls auto-hide, no persistent bar)
  2. Wants YouTube-like persistent control bar with seek + time labels
  3. Feels playback lag (emulator WebView software rendering)

---

## Goal

Replace the current overlay controls with a **persistent bottom control bar** (YouTube parity) that:
- Always shows seek bar + current/duration time
- Play/pause, ±10s skip, fullscreen, Max HD
- Auto-hides after 3s inactivity, tap to show
- Fixes lag by enabling WebView hardware acceleration + batching JS calls
- Reuses `HdPlayerController` WebView across video swaps (no recreation)

---

## Architecture

```
PlayerScreen
├── MinimalWebPlayer (AndroidView → WebView)
│   ├── HdPlayerController (JS bridge)
│   │   ├── play() / pause() / seekTo(s)
│   │   ├── setMaxQuality()
│   │   ├── getCurrentPosition() → Double (seconds)
│   │   ├── getDuration() → Double (seconds)
│   │   └── isPlaying() → Boolean
│   └── Injected CSS: pins #movie_player, hides chrome, disables scroll
├── BottomPlayerControls (Compose, persistent bar)
│   ├── Slider (seek) + time labels
│   ├── Play/Pause (center, 48dp)
│   ├── Skip ±10s (Replay10 / Forward10)
│   ├── Fullscreen (right)
│   └── Auto-hide: 3s inactivity → fade out
├── DoubleTapSkipOverlay (unchanged)
└── TouchLockOverlay (unchanged)
```

**Data Flow:**
```
HdPlayerController (500ms poll)
    └─→ PlayerScreen state: hdPosition, hdDuration, hdIsPlaying
            └─→ BottomPlayerControls reads state → renders slider position
                    └─→ User drags slider → onSeek(s) → HdPlayerController.seekTo(s)
```

---

## Components

### 1. `MinimalWebPlayer.kt` (NEW)
- Composable wrapping `AndroidView(WebView)`
- Loads `m.youtube.com/watch?v={videoId}`
- Injects `PLAYER_ONLY_CSS` on `onPageFinished`
- Disables WebView touch (`setOnTouchListener { true }`) — Compose handles gestures
- Hardware acceleration: `setLayerType(LAYER_TYPE_HARDWARE)`, `renderPriority = HIGH`
- Reuses controller's WebView via `controller.loadVideo(videoId)` (no recreate)

### 2. `BottomPlayerControls.kt` (NEW)
```kotlin
@Composable
fun BottomPlayerControls(
    duration: Float,           // seconds
    currentPosition: Float,    // seconds
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onMaxQuality: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
)
```
- Full-width bar at bottom of player area
- `Slider` with played track (primary), buffered track (outline/30% alpha), thumb
- Time labels: `MM:SS` or `HH:MM:SS` via `TimeUtils.formatDuration(seconds: Long)`
- Center: Play/Pause (48dp), ±10s beside it
- Right: Fullscreen + Max HD (IconButtons)
- `AnimatedVisibility(visible)` with fadeIn/fadeOut (300ms)

### 3. `HdPlayerController.kt` (MODIFY)
- Add `getDuration(onResult: (Double) -> Unit)` — calls `video.duration`
- Add `getState(onResult: (PlayerState) -> Unit)` — **batched** call returning `{currentTime, duration, paused}` in one JS round-trip
- Keep `loadVideo(videoId: String)` for swap without WebView recreation
- Keep `setMaxQuality()` — forces highest quality level

### 4. `PlayerScreen.kt` (REFACTOR)
- Replace `HdWebPlayer` + `HdPlayerControls` with `MinimalWebPlayer` + `BottomPlayerControls`
- Single `LaunchedEffect` polling `controller.getState()` every 500ms → updates `hdPosition`, `hdDuration`, `hdIsPlaying`
- Touch routing:
  - If `isTouchLocked` → `TouchLockOverlay` swallows all
  - Else `Box(Modifier.fillMaxSize().pointerInput(...))`:
    - Double-tap left 1/3 → `onSkipBackward`
    - Double-tap right 1/3 → `onSkipForward`
    - Single tap center → `controlsVisible = !controlsVisible`
- Fullscreen: `WindowInsetsController` + `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`
- PiP: unchanged
- Download/Like/Share: unchanged (below player in LazyColumn)

### 5. `HdPlayerControls.kt` → **DELETE**
### 6. `HdWebPlayer.kt` → **DELETE**

---

## Lag Mitigation (Performance)

| Issue | Fix |
|-------|-----|
| Emulator WebView software render | `webView.layerType = View.LAYER_TYPE_HARDWARE`; `settings.setRenderPriority(WebSettings.RenderPriority.HIGH)` |
| JS bridge latency (500ms × 3 calls) | **Batch**: single `getState()` JS call returns `{currentTime, duration, paused}` |
| Compose recomposition on slider drag | `BottomPlayerControls` reads `Float` state — only slider thumb recomposes |
| Initial load delay | Reuse WebView: `controller.loadVideo(newId)` instead of new `AndroidView` + WebView |
| Watchdog JS thrashing | Reduce interval from 5s → 10s (CSS is stable after first inject) |

---

## Touch Handling — No Conflicts

| Gesture | Zone | Handler |
|---------|------|---------|
| Single tap | Center 1/3 | Toggle `controlsVisible` |
| Double tap | Left 1/3 | Seek -10s (`DoubleTapSkipOverlay`) |
| Double tap | Right 1/3 | Seek +10s (`DoubleTapSkipOverlay`) |
| Drag | Slider thumb | `BottomPlayerControls` Slider internal |
| Any | `isTouchLocked=true` | `TouchLockOverlay` swallows |

Implementation: Single `pointerInput` in `PlayerScreen` routes by `pressCount` + `offset.x / width`.

---

## Acceptance Criteria (Emulator QA)

1. **Persistent seek bar visible** on player load (no tap needed)
2. **Time labels** show `current / duration` (e.g., `1:23 / 10:45`)
3. **Drag seek** jumps video to dragged position
4. **Play/Pause** toggles video
5. **±10s skip** works (buttons + double-tap)
6. **Fullscreen** rotates to landscape, hides system bars
7. **Max HD** forces highest quality
8. **Auto-hide**: controls fade after 3s inactivity; tap → fade in
9. **Double-tap skip** shows animated indicator (existing)
10. **Lock** disables all gestures; unlock button shows
11. **No lag**: smooth 60fps scrub, no stutter on play/pause (emulator)
12. **Video swap** (related video tap) reuses WebView — no flash/reload

---

## Files Changed

| File | Action |
|------|--------|
| `MinimalWebPlayer.kt` | CREATE |
| `BottomPlayerControls.kt` | CREATE |
| `HdPlayerController.kt` | MODIFY (add `getDuration`, `getState`) |
| `PlayerScreen.kt` | REFACTOR (swap components, touch routing) |
| `HdPlayerControls.kt` | DELETE |
| `HdWebPlayer.kt` | DELETE |

---

## Risks / Open Questions

1. **WebView hardware accel on emulator** — may still software-render if host GPU passthrough disabled. Acceptable; real devices are fine.
2. **YouTube JS API changes** — `getState()` relies on `movie_player` / `<video>` selectors. Mitigation: fallback to individual calls if batched fails.
3. **Duration 0 initially** — `getState` may return 0 until metadata loads. `BottomPlayerControls` handles `duration <= 0` (shows `--:--`).

---

## Next Steps

1. **User reviews this spec** → approve or request changes
2. **Invoke `writing-plans` skill** → generate implementation plan
3. **Execute plan** in fresh session or continue

---

**Spec written to:** `docs/superpowers/specs/2026-07-23-universal-player-seekbar-lag-fix-design.md`  
**Commit:** pending user approval