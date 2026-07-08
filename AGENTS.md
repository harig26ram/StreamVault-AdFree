# FreedomPlay — StreamVault-AdFree

Ad-free YouTube streaming Android app. Scrapes YouTube InnerTube API directly. Custom MediaCodec player, SponsorBlock, Material 3 AMOLED UI.

## Quick Reference

- **Package**: `com.streamvault.app`
- **Min SDK**: 24 (Android 7.0) | **Target SDK**: 36 (Android 16)
- **Version**: 4.0.0 (versionCode 4)
- **Branch**: `dev`

## Build

```bash
.\gradlew.bat assembleDebug          # Debug APK
.\gradlew.bat assembleRelease        # Release (needs signing config)
.\gradlew.bat testDebugUnitTest      # Unit tests (192 tests)
.\gradlew.bat lintDebug              # Lint
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **Architecture**: MVVM, Clean Architecture (data/domain/presentation)
- **DI**: Hilt (Dagger 2.50)
- **DB**: Room 2.6.1 (version 4, MIGRATION_3_4)
- **Network**: Retrofit 2.9.0 + OkHttp 4.12.0, Gson
- **Player**: Custom MediaCodec pipeline (4 modules under `player/`)
- **Auth**: Google Sign-In with auto token refresh + silent sign-in
- **Images**: Coil 2.5.0

## Project Structure

```
app/src/main/java/com/streamvault/app/
  auth/              — AuthManager (token refresh, silent sign-in, session persistence)
  data/
    api/             — YouTubeApiService, InnerTube DTOs
    local/           — Room DAO (version 4), entities (watch_history, subscriptions, watch_later)
    model/           — InnerTube JSON models
    repository/      — VideoRepositoryImpl (personalized feed, fallback chain)
  di/                — Hilt modules (NetworkModule w/ 401 interceptor, DatabaseModule w/ MIGRATION_3_4)
  domain/
    model/           — Video, Channel, FeedItem, SearchResult
    repository/      — VideoRepository interface
    usecase/         — GetHomeFeedUseCase, SearchUseCase, GetPlayerUseCase
  presentation/
    MainActivity.kt  — Single activity, FreedomPlayTheme
    navigation/      — NavGraph.kt, Screen.kt (Home, Trending, Search, Subscriptions, Library, Settings)
    ui/components/   — BottomNavBar, VideoCard, LoadingIndicator, MiniPlayer
    ui/screen/       — HomeScreen, TrendingScreen, SearchScreen, PlayerScreen, SubscriptionsScreen, etc.
    ui/theme/        — Theme.kt (AmoledColorScheme, DarkColorScheme, LightColorScheme)
    viewmodel/       — HomeViewModel, TrendingViewModel, SearchViewModel, PlayerViewModel, etc.
  service/           — PlaybackService (background playback w/ audio focus)
  notification/      — NotificationHelper, SubscriptionCheckWorker
  data/download/     — DownloadManager, DownloadWorker
  data/cast/         — CastSessionManager, CastPlayer, CastDialog, CastOptionsProvider
  util/              — TimeUtils, UrlUtils

player/core/         — PlayerEngine, DecoderThread, AudioTrackBufferProvider, PlaybackClock, EqualizerManager, EqualizerManagerHolder
player/youtube/      — StreamUrlExtractor, CipherDecryptor, NParamDecryptor, PlayerJsFetcher
player/ui/           — PlayerView composable, MiniPlayer, GestureOverlay, QueueBottomSheet
player/sponsorblock/ — SponsorBlock integration
```

## Bug Fixes (all resolved)

### Bug 1: Hardcoded "StreamVault" ✅ FIXED
- `HomeScreen.kt:77` — `stringResource(R.string.app_name)`
- `SettingsScreen.kt:198` — `stringResource(R.string.app_name)`
- `SettingsScreen.kt:311` — `stringResource(R.string.app_name)`

### Bug 2: Search 401 — visitorData desync ✅ FIXED
- `VisitorDataBootstrapper.kt` — Removed `_bootstrapAttempted` one-shot guard
- `VideoRepositoryImpl.kt` — 4 methods now fall back to `NetworkModule.visitorData` when `cachedVisitorData` is null
- `fetchSearchBasedHomeFeed()` — Now injects visitorData into SearchRequest context

### Bug 3: Icon — anime cat vector ✅ FIXED
- `drawable/ic_launcher_foreground.xml` — Anime cat vector (108dp)
- `drawable/ic_launcher_background.xml` — Pink-purple gradient with stars
- `drawable/ic_launcher.xml` — 48dp standalone version
- `AndroidManifest.xml` — Changed `@drawable` → `@mipmap` references

## Features Implemented

### Auth (Wave 1 — Agent A)
- **Token expiry tracking**: `KEY_TOKEN_EXPIRY` stored in SharedPreferences
- **Session validation**: `restoreSession()` checks expiry, attempts refresh
- **Silent sign-in**: `googleSignInClient.silentSignIn()` on app start
- **Refresh with lock**: `synchronized(refreshLock)` prevents token refresh stampede
- **Revoke on sign-out**: `revokeAccess()` before `signOut()`
- **401 interceptor**: OkHttp interceptor catches 401, refreshes token, retries request

### Feed (Wave 1 — Agent B + Wave 2A)
- **Personalized home feed**: `FEwhat_to_watch` browse endpoint (authenticated) → HTML scrape → search fallback
- **Search history topics**: Reads from SharedPreferences, uses as search fallback topics
- **Watch history integration**: Extracts channel IDs + keywords from Room DB, seeds related searches
- **Subscriptions feed**: InnerTube `FEsubscriptions` API → video feed above channel list
- **Trending screen**: New screen with category tabs (All/Music/Gaming/Movies), shimmer loading
- **Infinite scroll**: Continuation token support for paginated loads

### Premium Features (Wave 1 — Agent C)
- **Background playback**: PlaybackService with audio focus handling, foreground notification
- **PiP mode**: Enter/exit PiP, lifecycle polling, resume dialog
- **Mini player**: Floating overlay with thumbnail, progress bar, queue button, slide-up gesture
- **Queue management**: Add/remove/reorder/shuffle, repeat modes (OFF/ONE/ALL), drag-reorder UI
- **Equalizer**: EqualizerManager (Equalizer, BassBoost, Virtualizer), persistent settings
- **Skip silence**: Position-stall heuristic (500ms stall → seek forward 1s)
- **Remember position**: Room `last_position_ms` column, resume dialog on reopen

### UI Polish (Wave 2B)
- **Shimmer loading**: Animated gradient placeholders on TrendingScreen
- **Horizontal channel row**: Subscription avatars with pink ring indicators
- **Queue bottom sheet**: Drag handle, prominent shuffle/repeat, current track indicator
- **Gradient headers**: Subtle primary-colored gradient behind TopAppBars
- **Mini player**: Progress bar, queue button, slide-up gesture

### Downloads (Wave 3A)
- **DownloadManager**: WorkManager orchestration — start/pause/cancel/delete per video
- **DownloadWorker**: @HiltWorker, downloads audio+video via HttpURLConnection, muxes with MediaMuxer, progress notifications
- **DownloadEntity**: Room entity (video_id PK, title, channel_name, thumbnail_url, file_path, file_size, download_status, progress, downloaded_at)
- **PlayerViewModel**: Full download controls wired to player formats
- **LibraryViewModel**: Downloads list in Library tab

### Equalizer UI (Wave 3B)
- **EqualizerScreen**: Full AMOLED equalizer — preset chips, vertical band sliders, bass/virtualizer sliders
- **EqualizerViewModel**: HiltViewModel persisting to SettingsManager
- **EqualizerManagerHolder**: Singleton bridge across navigation
- **PlayerScreen**: Equalizer button (pink when active)
- **SettingsScreen**: Audio Equalizer toggle in Features section

### Cast (Wave 3C — Coming Soon)
- **CastSessionManager**: SDK availability detection via `CastContext.getSharedInstance()`, session lifecycle
- **CastPlayer**: Stub methods (play/pause/seek/toggle/stop)
- **CastDialog**: Device picker UI with connect/disconnect
- **HomeScreen**: Cast button hidden when SDK unavailable
- **Note**: Requires Cast Receiver App ID from Google Cast Developer Console to activate

### Subscription Notifications (Wave 3D)
- **NotificationHelper**: Creates notification channels, schedules 6-hour periodic WorkManager check
- **SubscriptionCheckWorker**: @HiltWorker, fetches FEsubscriptions feed, compares video IDs, posts summary notification
- **Wired in StreamVaultApplication.onCreate()**: Channels created + worker scheduled on app start
- **POST_NOTIFICATIONS**: Runtime permission requested in MainActivity (Android 13+)

## Secrets

- `secrets.properties` — `WEB_CLIENT_ID`, `WEB_CLIENT_SECRET` (placeholder, needs real Google Cloud OAuth)
- `local.properties` — `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
- Both are gitignored

## Security Notes

- `allowBackup=false` — prevents ADB data extraction
- ProGuard strips ALL Log calls in release builds
- HttpLoggingInterceptor is debug-only (BuildConfig.DEBUG)
- Hilt ActivityContextWrapper + PlaybackService keep rules in proguard-rules.pro
- OAuth token refresh synchronized with `refreshLock` to prevent concurrent refreshes

## Testing

- **Unit tests**: 192 tests (90 player modules + 102 app module)
- **Player modules**: PlaybackClockTest, PlayerEngineTest, CipherDecryptorTest, ItagInfoTest, NParamDecryptorTest, StreamUrlExtractorTest, SponsorBlockApiTest, SponsorBlockManagerTest, GestureOverlayTest, PlayerControlsTest
- **App module**: TimeUtilsTest, UrlUtilsTest, HomeViewModelTest, SearchViewModelTest
- **Integration**: Test on emulator (API 37) and OnePlus 13R (real device)
- **No instrumented tests yet** (no androidTest/ directory)

## Conventions

- Kotlin, Compose, Material 3
- MVVM with UseCase layer
- Hilt dependency injection everywhere
- Sealed classes for UI state (UiState with isLoading/error/data)
- Hardcoded strings in Compose = BUG — always use `stringResource(R.string.xxx)`
- `applicationId = "com.streamvault.app"` (package name unchanged from StreamVault era)
- AMOLED theme: pure black (#000000) backgrounds, hot pink (#FF4081) accents
