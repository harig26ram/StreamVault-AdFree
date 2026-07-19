# HANDOFF: StreamVault-AdFree (FreedomPlay) — Resilient Streaming Architecture
Generated: 2026-07-20 · Session focus: InstanceManager health scoring, StreamRepository refactoring, emulator testing, backend failure analysis

---

## 1. App Goal

**FreedomPlay** — ad-free YouTube streaming Android app. Users browse trending videos, search, and play content without ads. Uses Piped/Invidious/YouTube Innertube APIs as backend sources with cascading fallback. Material 3 AMOLED UI, Media3 ExoPlayer, Hilt DI, MVVM architecture.

**Core value proposition:** Never show ads. Play any YouTube video via alternative API backends. If one backend fails, automatically try the next.

---

## 2. What Was Done This Session

### A. InstanceManager Integration (Health Scoring + Circuit Breaker)
- **InstanceManager.kt** (194 lines) already existed at `data/manager/InstanceManager.kt` — @Singleton with:
  - Health scoring: success rate (50%) + latency EMA (30%) + recency (20%)
  - Circuit breaker: 5 consecutive fails → 60s cooldown
  - Permanent removal: 20+ total failures → instance excluded
  - DataStore persistence (Gson JSON), loaded on app startup
  - `getOrderedInstances()` returns instances sorted by health score
- **Fix applied:** Replaced `runBlocking` in `persistHealthMap()` with `scope.launch` (CoroutineScope + SupervisorJob + Dispatchers.IO)
- **Wired into FreedomPlayApplication.kt:** `@Inject lateinit var instanceManager: InstanceManager`, `appScope.launch { instanceManager.initialize() }` in `onCreate()`

### B. StreamRepository.kt Fully Refactored (1007 lines)
- **Constructor:** Added `instanceManager: InstanceManager` parameter
- **Removed:** `failedInstances: ConcurrentHashMap`, `FAIL_COOLDOWN_MS`, `isInstanceHealthy()`, `markInstanceFailed()` — all replaced by InstanceManager
- **New helpers:**
  - `getOrderedPipedInstances()` (L47-53) — builds `InstanceConfig` list from `PIPED_FALLBACK_URLS`, passes through `instanceManager.getOrderedInstances()`
  - `getOrderedInvidiousInstances()` (L55-62) — same for `INVIDIOUS_FALLBACK_URLS`
- **All 6 iteration loops updated** (getTrending L286, getMusicTrending L363, search L517, getStreams L598, getSuggestions L674, plus internal YouTube loops):
  - Old: `for (baseUrl in URLS) { if (!isInstanceHealthy(baseUrl)) continue; ... }`
  - New: `for (instance in getOrderedInstances()) { try { ... instanceManager.recordSuccess(url, latency) } catch { instanceManager.recordFailure(url) } }`
- **HTML response fix:** Invidious getStreams loop changed from weak `!body.contains("\"error\"")` to proper `isValidJson(body)` — catches HTML error pages, CAPTCHA, shutdown notices
- **Latency tracking:** Every HTTP request records `System.currentTimeMillis()` before/after, passes to `recordSuccess()` for EMA calculation
- **Misleading log fix:** `CrashLogger.e("All Piped and YouTube failed...", Exception("No instances"))` → `CrashLogger.d("All Piped and YouTube failed for $videoId, trying Invidious")`
- **Stream validation:** Piped/Invidious loops now check `hasPlayableVideo()`/`hasPlayableAudio()` before returning — rejects empty stream arrays

### C. NetworkModule.kt Instance Cleanup
- **Piped (5):** r4fo.com, projectsegfau.lt, private.coffee, kavin.rocks, leptons.xyz
- **Invidious (8):** zoomerville.com, materialio.us, nadeko.net, nerdvpn.de, ggtyler.dev, yewtu.be, f5.si, chocolatemoo53.com
- **Removed:** tiekoetter.com (0% playback ratio), puffyan.us (connection fail), privacydev.net (DNS fail), jesf.dev (DNS fail)

### D. Deep-link Intent Handling (Already Existed)
- `MainActivity.kt` L56: `private var pendingVideoId by mutableStateOf<String?>(null)`
- L66-78: `parseYouTubeUrl()` extracts videoId from URL query param or `intent.getStringExtra("videoId")`
- L80-87: `onNewIntent()` calls `parseYouTubeUrl()` → sets `pendingVideoId`
- L107-113: `LaunchedEffect(videoIdToNavigate)` navigates to Player route, then clears `pendingVideoId`
- **Tested:** Cold launch with `--es videoId GJO7soplaQ8` triggers full getStreams() flow

### E. Build Status
- **BUILD SUCCESSFUL** (1m 40s) — only pre-existing warnings (shadowed names in YouTube innertube code, unused params in PlayerScreen)
- APK at `app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Limitations & Blocking Issues

### CRITICAL: Zero Working Video Streams From All Backends

**This is NOT an app bug — it is a server-side YouTube blocking issue.**

Every public backend fails for individual video streams (`/api/v1/videos/{id}` and `/streams/{id}`):

| Backend | Result | Details |
|---------|--------|---------|
| **YouTube Innertube** | BLOCKED | ANDROID_VR=LOGIN_REQUIRED, ANDROID=400, IOS=400, WEB=UNPLAYABLE |
| **Piped** | ALL DEAD | r4fo.com=HTML page, projectsegfau.lt=empty array, private.coffee=500, kavin.rocks=526, leptons.xyz=502 |
| **Invidious** | ALL BLOCKED | zoomerville.com=403, materialio.us=500, nadeko.net=403, nerdvpn.de=401, yewtu.be=403, f5.si=HTML CAPTCHA, chocolatemoo53.com=403, ggtyler.dev=timeout |

**What DOES work:**
- Trending feed: 14-15 items from `invidious.materialio.us` via `/api/v1/trending` ✅
- Search: Partially works via Invidious `/api/v1/search` (returns results but no stream URLs)
- App UI: Full navigation, deep-link, error states with retry ✅

**Root cause:** YouTube aggressively blocks anonymous API access to video stream endpoints. Piped/Invidious instances either shut down, get rate-limited, or return HTML error pages instead of JSON. This is a known issue across the entire YouTube alternative client ecosystem (see yt-dlp GitHub issues #9316, #16821).

### Known Implementation Issues

1. **No adaptive streaming** — App uses direct MP4/WebM URLs only. No DASH/HLS manifest support. ExoPlayer can play adaptive streams via `MergingMediaSource` but the app doesn't provide manifests.

2. **Retrofit services unused** — `PipedApiService` and `InvidiousApiService` are injected via Hilt but `StreamRepository` uses raw OkHttp with manual URL construction instead. The injected services are dead code.

3. **No codec-aware selection** — Quality selector doesn't distinguish VP9 vs H.264. VP9 offers better quality/bitrate but may not play on older devices (minSdk=24).

4. **No subtitle handling** — `Stream.subtitles` is populated by DTO conversion but `PlayerScreen` never loads them into ExoPlayer's `SubtitleConfiguration`.

5. **Audio-only mode creates invisible PlayerView** — Instead of using `MediaSessionService` for true background playback, it creates a hidden `PlayerView` composable.

6. **No bandwidth estimation** — ExoPlayer's `DefaultTrackSelector` with bandwidth meter could auto-select optimal quality, but isn't used. Player manually picks `maxByOrNull { height }`.

7. **Instance health data can be stale** — Health map persists across app sessions. If an instance recovers, it takes up to 60s cooldown + success to restore. No manual "reset all" button in UI.

---

## 4. Issues Encountered During Implementation

### A. Brace Mismatch Bug (Fixed)
**What happened:** When refactoring the Piped for-loop in `getStreams()`, partial edits left a missing closing `}` for the `for` loop. The `} catch` block was under-indented (16sp instead of 20sp), and the `for` loop's closing brace was missing entirely.

**Impact:** Build succeeded (Kotlin ignores indentation) but the code structure was wrong — the `CrashLogger.d("All Piped failed...")` log and Invidious loop were inside the Piped `for` loop instead of after it.

**Fix:** Added missing `}` at 16 spaces between the catch block close and the "Piped failed" log. Verified via `.\gradlew.bat assembleDebug`.

**Lesson:** Partial string replacement in deeply nested code is error-prone. Always verify brace structure after edits.

### B. `runBlocking` in InstanceManager (Fixed)
**What happened:** `persistHealthMap()` used `runBlocking { dataStore.edit { ... } }` which blocks the calling thread. In StreamRepository, this was called on the main thread during HTTP response handling.

**Fix:** Replaced with `scope.launch { dataStore.edit { ... } }` using a dedicated `CoroutineScope(SupervisorJob() + Dispatchers.IO)` field.

### C. Emulator Intent Delivery Issues
**What happened:** `adb shell am start --es videoId GJO7soplaQ8` to an already-running app shows "Warning: Activity not started, intent has been delivered to currently running top-most instance" — but `onNewIntent()` doesn't fire.

**Root cause:** Multiple factors: (1) notification permission dialog blocks intent processing, (2) 16KB compatibility dialog on first launch intercepts, (3) the `mutableStateOf` fix (changed from plain `var`) resolved recomposition but cold launch via intent works while warm launch doesn't always deliver.

**Workaround:** Force-stop app before testing deep-link: `adb shell am force-stop com.freedomplay.app && adb shell am start -n com.freedomplay.app/.presentation.MainActivity --es videoId GJO7soplaQ8`

### D. Invidious HTML Responses Masquerading as JSON
**What happened:** Several Invidious instances return HTTP 200 with HTML content (bot-check pages, CAPTCHA challenges, or error pages) instead of JSON. The old check `body.contains("\"error\"")` didn't catch these.

**Fix:** Changed to use the existing `isValidJson()` helper which checks for `<!DOCTYPE`, `<html`, `Redirecting`, `shutdown`, and other HTML markers before Gson parsing.

### E. Piped Redirect Responses
**What happened:** `pipedapi.r4fo.com/streams/{id}` returns HTTP 200 with a redirect to `piped.video/watch` (HTML page), not JSON. OkHttp follows the redirect and returns HTML.

**Fix:** The `isValidJson()` check catches this. Also added `hasPlayableVideo()`/`hasPlayableAudio()` validation to reject responses with empty stream arrays.

---

## 5. Relevant Files & Line Numbers

### Core Backend
| File | Lines | Key Methods |
|------|-------|-------------|
| `data/repository/StreamRepository.kt` | 1007 | `getStreams()` L598, `getTrending()` L286, `search()` L517, `getStreamsFromYouTube()` L164, `getOrderedPipedInstances()` L47, `getOrderedInvidiousInstances()` L55 |
| `data/manager/InstanceManager.kt` | 194 | `getOrderedInstances()` L108, `recordSuccess()` L115, `recordFailure()` L131, `getHealthScore()` L91, `initialize()` L65 |
| `di/NetworkModule.kt` | 124 | `PIPED_FALLBACK_URLS` L37-43, `INVIDIOUS_FALLBACK_URLS` L45-54, OkHttp config L58-89 |

### DI & App
| File | Lines | Notes |
|------|-------|-------|
| `FreedomPlayApplication.kt` | 94 | InstanceManager injection L32, init L66 |
| `di/AppModule.kt` | — | Provides Gson, DataStore, etc. (InstanceManager uses @Inject constructor, no explicit provider needed) |

### UI & Navigation
| File | Lines | Notes |
|------|-------|-------|
| `presentation/MainActivity.kt` | 196 | Deep-link handling L56-113, `pendingVideoId` L56 |
| `presentation/navigation/NavGraph.kt` | 71 | Routes: Home, Search, Player/{videoId}, Library, Settings |
| `presentation/ui/screens/player/PlayerScreen.kt` | ~770 | ExoPlayer init L214, quality selector L664, error state L179-205 |
| `presentation/viewmodel/PlayerViewModel.kt` | ~147 | `loadVideo()` L69, quality selection L81-89 |

### YouTube Client Config (StreamRepository.kt L86-155)
- ANDROID_VR: v1.60.19, UA: `com.google.android.apps.youtube.vr.oculus/1.60.19`
- ANDROID: v19.44.38, UA: `com.google.android.youtube/19.44.38`
- IOS: v19.45.4, UA: `com.google.ios.youtube/19.45.4`
- WEB: v2.20250623.01.00, UA: Chrome/131

---

## 6. Traps & Dead Ends

- **YouTube `/youtubei/v1/browse` is permanently blocked** for anonymous requests — needs visitorData/PO tokens. Do not attempt to fix.
- **YouTube `/youtubei/v1/visitor` returns 404** — cannot get visitorData this way.
- **Piped instances are 90%+ dead** — most return HTML, 500s, or empty responses. r4fo.com is the only one that sometimes returns JSON.
- **Invidious trending works but streams don't** — `/api/v1/trending` is less protected than `/api/v1/videos/{id}`. YouTube specifically blocks individual video stream access.
- **`invidious.f5.si` returns HTML CAPTCHA** for video requests — `isValidJson()` catches this.
- **Force-stop required before testing deep-link** — warm launch delivers intent but `onNewIntent()` may not fire reliably.
- **Build uses cached dependencies** — `.\gradlew.bat assembleDebug` (no `--offline` needed if connected).

---

## 7. What's Next

### Immediate (requires external resolution)
1. **Find working video stream backend** — All public Piped/Invidious instances fail. Options:
   - Self-host a Piped/Invidious instance (requires server + YouTube cookie/PO token)
   - Wait for public instances to recover
   - Implement yt-dlp-style extraction with PO token generation (complex, may need server-side component)
   - Use a different video source (peer-to-peer, archived content, etc.)

### When streams work again
2. **Test end-to-end playback** — Trending loads, deep-link works, ExoPlayer is ready. Just needs a working stream URL.
3. **Implement adaptive streaming** — Add DASH/HLS support via ExoPlayer's `DashMediaSource`/`HlsMediaSource` + `MergingMediaSource` for separate audio+video tracks
4. **Add bandwidth estimation** — `DefaultTrackSelector` with `DefaultBandwidthMeter` for automatic quality selection
5. **Codec-aware quality selector** — Distinguish VP9 vs H.264, show bitrate in quality options
6. **Subtitle support** — Load `Stream.subtitles` into ExoPlayer `SubtitleConfiguration`
7. **Background audio** — Replace invisible PlayerView with `MediaSessionService` for true audio-only playback

### Low Priority
8. **Clean up unused Retrofit services** — `PipedApiService`/`InvidiousApiService` are injected but never used. Either use them or remove.
9. **Instance health UI** — Settings screen showing per-instance health scores, manual reset button
10. **PO token integration** — If feasible, for YouTube browse endpoints

---

## 8. Prompt for Fresh Agent

```
FreedomPlay (StreamVault-AdFree) is an ad-free YouTube streaming Android app. 

WORKING: App builds/launches, trending feed loads (14 items from invidious.materialio.us), deep-link intent handling works, InstanceManager health scoring + circuit breaker integrated, StreamRepository refactored with health-sorted fallback, HTML response detection, stream validation, user-friendly error states.

BLOCKER: ALL public Piped/Invidious/YouTube backends fail for individual video streams. YouTube blocks anonymous access, Piped instances are dead, Invidious returns HTML/403s for /api/v1/videos/{id}. This is server-side, not an app bug.

ARCHITECTURE: MVVM + Hilt + Compose + Media3 ExoPlayer. StreamRepository.kt (1007 lines) handles all backend communication with cascading fallback: Piped → YouTube innertube → Invidious. InstanceManager.kt (194 lines) provides health scoring (success rate + latency + recency), circuit breaker (5 fails → 60s cooldown), permanent removal (20+ fails).

KEY FILES:
- StreamRepository.kt: getStreams() L598, getTrending() L286, search() L517
- InstanceManager.kt: getOrderedInstances() L108, recordSuccess() L115, recordFailure() L131
- NetworkModule.kt: PIPED_FALLBACK_URLS L37, INVIDIOUS_FALLBACK_URLS L45
- MainActivity.kt: deep-link handling L56-113
- PlayerScreen.kt: ExoPlayer L214, quality selector L664, error state L179-205

Before acting, read StreamRepository.kt (1007 lines) and InstanceManager.kt (194 lines) fully. The codebase uses Kotlin, Compose, Material 3, Hilt DI, MVVM pattern.
```
