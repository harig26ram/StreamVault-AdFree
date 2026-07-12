# HANDOFF — StreamVault-AdFree / FreedomPlay

## Context
App: **FreedomPlay / StreamVault-AdFree** (package `com.streamvault.app`), Android ad-free YouTube player.
Kotlin / Jetpack Compose / Material 3 / AMOLED. Version 6.0.0 (versionCode 6).
Build: `.\gradlew.bat compileDebugKotlin` — BUILD SUCCESSFUL.

## What's Done (All 11 Original Issues + 2 New Features)

### #1 Controls auto-hide
PlayerScreen.kt: controls wrapped in `if (showControls)`, auto-hide via inactivity timeout.

### #2 Fullscreen button + #8 Top bar video title
PlayerScreen.kt: fullscreen IconButton (Fullscreen/FullscreenExit icons), `DisposableEffect` for SENSOR_LANDSCAPE + hide system bars. Top bar shows title + channelName Column, both clickable.

### #3 YouTube parity — likeCount + Open in YouTube
- `likeCount: String` field on `Video` data class (MediaModels.kt).
- `VideoDetails.likeCount` DTO field (YouTubeApiService.kt:202).
- `detailsToVideo()` formats likeCount to K/M (VideoRepositoryImpl.kt:2377).
- PlayerScreen Like button shows `"Like ${video.likeCount}"` (PlayerScreen.kt:867).
- Open in YouTube: PlayerScreen (OpenInNew icon, try `com.google.android.youtube` package, fallback browser) + VideoCard dropdown.
- Dislike button (stub toast). Save/Watch Later works.

### #4 Logout / switch account
SettingsScreen: signOut() + onNavigateToLogin(). AuthManager.revokeAccess() + signOut().

### #5 Highest quality default
SettingsScreen qualities list expanded. `loadBestStream()` reads `settingsManager.videoQuality`, resolves targetHeight.

### #6 Pull-to-refresh — 7 screens
Home, Trending, Subscriptions, Search, Channel, Playlist, Library — all wrapped in `PullToRefreshBox`. Each screen's ViewModel has `isRefreshing` + `refresh()`.

### #7 Related videos loading
`getRelatedVideos()` uses `webContext()` + visitorData. `parseLockupViewModel()` fixed channelName assignment.

### #9 Download fixes (4 bugs)
1. **Resume resets progress** — `startDownload()` checks for existing PAUSED/FAILED entity, updates URLs + resets to PENDING (was inserting fresh 0-byte entity).
2. **deleteDownload missing temp cleanup** — Now deletes `.mp4`, `_audio.tmp`, `_video.tmp` from `downloads/` dir.
3. **Notification not auto-cancelled** — `nm.cancel(NOTIFICATION_ID)` after completion/failure.
4. **Added `updateDownloadUrls()`** DAO query (VideoDao.kt).

### #10 Autoplay setting honored
`playNextVideo()` returns early if `!settingsManager.autoplay` (after RepeatMode.ONE check).

### #11 reanime.to green theme
- **Theme.kt**: Blue (#008AC9) + purple (#2B115A) → VIVID GREEN (#22C55E) primary. Dark green containers (#0A2E14), near-invisible outlines (#1A201A), warm orange tertiary (#E8813B), muted error (#DC2626). Pure black AMOLED preserved.
- **VideoCard.kt**: Glassmorphism Surface (surfaceVariant @ 40% alpha, rounded 14dp). No heavy shadow.
- **MTricolorDivider.kt**: Blue/purple/red → green (#22C55E) / dark green (#0A2E14) / orange (#E8813B).

### Search Filters (new feature)
- **SearchViewModel.kt**: `SearchFilter` enum with InnerTube `sp` base64-encoded protobuf params (ALL, VIDEO, CHANNEL, PLAYLIST, LAST_HOUR, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, SHORT, MEDIUM, LONG).
- **SearchScreen.kt**: `LazyRow` of `FilterChip` composables between MTricolorDivider and content.
- **SearchUseCase.kt** + **VideoRepository.kt** + **VideoRepositoryImpl.kt**: `params: String?` plumbing to `SearchRequest`.
- Only visible when query is non-blank.

### Video Chapters (new feature)
- **MediaModels.kt:74-85**: `Chapter` data class (`title`, `startTimeMs`, computed `formattedTime` H:MM:SS / M:SS).
- **PlayerViewModel.kt**: `chapters: List<Chapter>` in `PlayerUiState`. `parseChaptersFromDescription()` in companion object using regex `^(\d{1,2}):(\d{2})(?::(\d{2}))?\s+(.+)$` with `RegexOption.MULTILINE`. Parsed when video info arrives.
- **PlayerScreen.kt:969-1008**: LazyColumn item between description and related videos. Only shown when `chapters.size >= 2`. Each row: timestamp (52dp) + title, highlighted in primary when current. Tapping seeks to chapter time.

## Already-Existing Features (found during audit)
- **Comments**: Full backend (Comment model, CommentData DTO, getComments() in VideoRepositoryImpl, GetCommentsUseCase) + UI (PlayerScreen.kt:992-1063).
- **Captions**: CaptionTrack, CaptionSelectorSheet (PlayerScreen.kt:1106-1146), VTT parsing.
- **Description expand/collapse**: PlayerScreen.kt:938-963.
- **Repeat modes**: OFF/ONE/ALL in transport controls.
- **Subscribe button**: Channel subscribe toggle (PlayerScreen.kt:846-860).
- **Queue management**: Add/remove/reorder/shuffle, repeat modes, drag-reorder UI.
- **Equalizer**: EqualizerManager, persistent settings, EqualizerScreen.
- **Mini player**: Floating overlay with thumbnail, progress bar, queue button.
- **Background playback**: PlaybackService with audio focus, foreground notification.
- **PiP mode**: Enter/exit PiP, lifecycle polling, resume dialog.
- **Download manager**: WorkManager orchestration, progress notifications.

## Status Table
| # | Feature | Status |
|---|---------|--------|
| 1 | Controls auto-hide | DONE |
| 2 | Fullscreen button | DONE |
| 3 | YouTube parity (likeCount, Open in YouTube) | DONE |
| 4 | Logout sign out | DONE |
| 5 | Highest quality default | DONE |
| 6 | Pull-to-refresh (7 screens) | DONE |
| 7 | Related videos loading | DONE |
| 8 | Top bar video name | DONE |
| 9 | Download fixes (resume/cleanup/notification) | DONE |
| 10 | Autoplay mechanism | DONE |
| 11 | reanime.to green theme | DONE |
| -- | Search Filters | DONE |
| -- | Video Chapters | DONE |

## Potential Next Features
1. **Channel tabs** — Videos, Playlists, About tabs on ChannelScreen
2. **Search history management** — Clear all, edit individual entries
3. **Better error states** — For comments/captions loading failures
4. **Description URL parsing** — Make links clickable in video description

## Key Files (absolute paths)
- PlayerScreen.kt: `app\src\main\java\com\streamvault\app\presentation\ui\screen\PlayerScreen.kt` (~1494 lines)
- PlayerViewModel.kt: `app\src\main\java\com\streamvault\app\presentation\viewmodel\PlayerViewModel.kt`
- MediaModels.kt: `app\src\main\java\com\streamvault\app\domain\model\MediaModels.kt`
- SearchViewModel.kt: `app\src\main\java\com\streamvault\app\presentation\viewmodel\SearchViewModel.kt`
- VideoRepositoryImpl.kt: `app\src\main\java\com\streamvault\app\data\repository\VideoRepositoryImpl.kt` (~2385 lines)
- DownloadManager.kt: `app\src\main\java\com\streamvault\app\data\download\DownloadManager.kt`
- DownloadWorker.kt: `app\src\main\java\com\streamvault\app\data\download\DownloadWorker.kt`
- Entities.kt: `app\src\main\java\com\streamvault\app\data\local\Entities.kt`
- Theme.kt: `app\src\main\java\com\streamvault\app\presentation\ui\theme\Theme.kt`
- VideoCard.kt: `app\src\main\java\com\streamvault\app\presentation\ui\components\VideoCard.kt`

## Build/Verify Loop
```
.\gradlew.bat compileDebugKotlin
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.streamvault.app/.presentation.MainActivity
```
