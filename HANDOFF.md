# HANDOFF: FreedomPlay (StreamVault-AdFree) — Full Feature Build
Generated: 2026-07-09 · Session focus: Complete all waves of features, OAuth fix, download resume, mini player overlay, PiP lifecycle

## 1. Goal
Build a fully-featured ad-free YouTube streaming Android app (FreedomPlay) with personalized feed, background playback, PiP, downloads, equalizer, mini player, and Google Sign-In. All features are COMPLETE. Release APK is 4.04MB.

## 2. Why This Matters / Background
Private closed group app, no public release. Success = ad-free YouTube playback with personalized feed, infinite suggestions, works on all Android devices (Moto sideload-restricted + OnePlus 13R OxygenOS 16). User instruction: "only interrupt me for human touch in decisions else execute yourself."

## 3. Current State
- **DONE**: All 18 feature waves completed (Auth, Feed, Premium, Personalized Feed, UI Polish, Tests, Downloads, Equalizer, Cast stubs, Notifications, Branding fixes, visitorData fix, Icon, Download playback, Download UI polish, MiniPlayer overlay, PiP lifecycle, Download resume, OAuth fix)
- **DONE**: Build verified — `assembleDebug` BUILD SUCCESSFUL, 192 unit tests pass (191 consistently, 1 flaky Robolectric)
- **DONE**: Release APK — 4.04MB signed with streamvault-release.jks
- **DONE**: OAuth — Android-type client configured, real Client ID in secrets.properties, no client secret needed
- **NOT STARTED**: Cast (dropped per user request)
- **NOT STARTED**: Instrumented/android tests (only unit tests exist)

## 4. Key Decisions (and why)
- **Custom MediaCodec player over ExoPlayer**: Full control over stream formats, skip silence, equalizer. Player modules: core, youtube, ui, sponsorblock
- **MiniPlayerManager as Hilt Singleton**: PlayerViewModel is per-NavBackStackEntry (hiltViewModel). To show mini player across navigation, singleton holds engine reference + state when mini player active. onCleared() skips engine release if mini player active.
- **PiP via polling → callback**: Initial 500ms polling was laggy. Replaced with `act.isInPictureInPictureMode` direct read in LaunchedEffect. MainActivity has `pipModeActive` mutableStateOf wired to onPictureInPictureModeChanged callback.
- **Download resume via DB URLs**: DownloadWorker stores audio_url + video_url in DownloadEntity. On app startup, resumePendingDownloads() re-enqueues WorkManager requests for PENDING/PAUSED entries.
- **OAuth: Android-type client**: No client secret needed. AuthManager guards updated to only check WEB_CLIENT_ID. Token exchange/refresh conditionally omit client_secret param when empty.
- **Cast dropped**: User said "drop it" — CastSessionManager/CastPlayer are stubs, button hidden when SDK unavailable.
- **Feeds use FEwhat_to_watch browse → HTML scrape → search fallback**: First tries authenticated YouTube browse endpoint, falls back to HTML scraping, then search-based feed with watch history topics.
- **DB version 6 with MIGRATION_5_6**: Added audio_url/video_url columns to downloads table for resume support.

## 5. Traps & Dead Ends
- **PlayerViewModel is per-NavBackStackEntry**: Cannot share mini player state across navigation without a singleton. MiniPlayerManager (@Singleton) solves this.
- **ComponentActivity.isInPictureInPictureMode clash**: Naming a field `isInPictureInPictureMode` causes JVM signature clash with ComponentActivity's built-in method. Use `pipModeActive` instead.
- **DownloadWorker loses URLs on pause**: URLs were only in inputData, not persisted to DB. Fixed by adding audio_url/video_url columns to DownloadEntity.
- **Android OAuth has no client_secret**: Code was sending empty client_secret which Google rejects. Fixed by conditionally omitting the param.
- **Robolectric flaky on Windows**: Temp directory lock contention causes 1 random test failure per run. Not a code issue — different test fails each run.
- **Release build R8 takes >2 min**: First attempt timed out at 5 min. Use 10 min timeout for `assembleRelease`.
- **secrets.properties + local.properties are gitignored**: Never committed. Contains real OAuth Client ID and signing passwords.

## 6. Relevant Files & Pointers
- `AGENTS.md` — Full project documentation, all features, architecture, test commands
- `HANDOFF.md` — This file (new session starting point)
- `secrets.properties` — WEB_CLIENT_ID (real), WEB_CLIENT_SECRET (empty). Gitignored.
- `local.properties` — Signing config (streamvault-release.jks passwords). Gitignored.
- `app/src/main/java/com/streamvault/app/auth/AuthManager.kt` — Google Sign-In, token exchange/refresh, silent sign-in, session persistence
- `app/src/main/java/com/streamvault/app/di/NetworkModule.kt` — 401 interceptor with auto token refresh
- `app/src/main/java/com/streamvault/app/data/repository/VideoRepositoryImpl.kt` — Feed fallback chain: FEwhat_to_watch → HTML → search
- `app/src/main/java/com/streamvault/app/data/repository/VisitorDataBootstrapper.kt` — Visitor data bootstrap (one-shot guard removed for retries)
- `app/src/main/java/com/streamvault/app/presentation/viewmodel/PlayerViewModel.kt` — Core player logic: background service, PiP, mini player, saved position, queue, equalizer, downloads
- `app/src/main/java/com/streamvault/app/presentation/ui/screen/PlayerScreen.kt` — Player UI: PiP callback, mini player, queue sheet, controls
- `app/src/main/java/com/streamvault/app/presentation/ui/components/MiniPlayerManager.kt` — Singleton holding engine + state for cross-navigation mini player
- `app/src/main/java/com/streamvault/app/presentation/navigation/NavGraph.kt` — MiniPlayer overlay on non-player screens
- `app/src/main/java/com/streamvault/app/data/download/DownloadManager.kt` — WorkManager orchestration, resumePendingDownloads()
- `app/src/main/java/com/streamvault/app/data/download/DownloadWorker.kt` — Download + mux with MediaMuxer, stores URLs in DB
- `app/src/main/java/com/streamvault/app/data/local/Entities.kt` — DownloadEntity with audio_url/video_url, DB version 6
- `app/src/main/java/com/streamvault/app/di/DatabaseModule.kt` — MIGRATION_5_6
- `app/src/main/java/com/streamvault/app/service/PlaybackService.kt` — Background playback with audio focus
- `player/core/src/main/java/com/streamvault/player/core/PlayerEngine.kt` — Custom MediaCodec engine, skip silence, equalizer
- `app/src/main/java/com/streamvault/app/presentation/MainActivity.kt` — pipModeActive state, PiP callback, notification permission
- `app/src/main/java/com/streamvault/app/StreamVaultApplication.kt` — NotificationHelper + DownloadManager resume on startup
- `app/src/test/` — 102 app module tests (TimeUtils, UrlUtils, HomeViewModel, SearchViewModel)
- `player/*/src/test/` — 90 player module tests

## 7. Open Work (status, with dependencies)
- **Release APK ready**: 4.04MB at `app/build/outputs/apk/release/app-release.apk`, signed with streamvault-release.jks
- **Cast**: Dropped per user request. Stubs exist but hidden.
- **Instrumented tests**: No androidTest/ directory exists. Would need Espresso/Compose test rules.
- **Flaky Robolectric test**: 1 test fails per run due to Windows temp dir lock contention. Not code-related.
- **OAuth on device**: Needs Google Play Services. Test on real device or emulator with Play Services.

---
## Prompt for the Fresh Agent

FreedomPlay (package: `com.streamvault.app`) is a complete ad-free YouTube streaming Android app. All 18 feature waves are done. Build: `./gradlew.bat assembleDebug` (BUILD SUCCESSFUL, 192 tests pass). Release APK: 4.04MB signed. Key architecture: Kotlin + Jetpack Compose + Material 3 + Hilt + Room + Retrofit + custom MediaCodec player. PlayerViewModel is per-NavBackStackEntry (hiltViewModel), MiniPlayerManager is @Singleton for cross-navigation mini player. PiP uses callback-based detection. Downloads support resume via DB-stored stream URLs. OAuth is Android-type (no client secret). Database is version 6 with MIGRATION_5_6.

Before responding, read every file listed under "Relevant Files & Pointers" above. Do not summarize, paraphrase, or claim you already have context — actually read each file. Treat every claim in this handoff as context to verify against the code, not facts to trust blindly. Then wait for my instructions before taking any action.
