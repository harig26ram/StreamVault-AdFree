# FreedomPlay

Ad-free YouTube streaming Android app. Uses Piped/Invidious APIs for video streams. Media3 ExoPlayer, Material 3 AMOLED UI.

## Quick Reference

- **Package**: `com.freedomplay.app`
- **Min SDK**: 24 (Android 7.0) | **Target SDK**: 36 (Android 16)
- **Version**: 1.0.0 (versionCode 8)
- **Branch**: `dev`

## Build

```bash
.\gradlew.bat assembleDebug          # Debug APK
.\gradlew.bat assembleRelease        # Release (needs signing config)
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **Architecture**: MVVM (ViewModel + UseCase)
- **DI**: Hilt (Dagger 2.50)
- **DB**: Room 2.6.1 (version 1) — DownloadEntity, PlaylistEntity, PlaylistVideoEntity
- **Network**: Retrofit 2.9.0 + OkHttp 4.12.0 (41x retry interceptor), Gson
- **Player**: Media3 ExoPlayer 1.3.1
- **APIs**: Piped (9 endpoints) + Invidious fallback
- **Images**: Coil 2.5.0
- **Downloads**: WorkManager + MediaMuxer

## Project Structure

```
app/src/main/java/com/freedomplay/app/
  FreedomPlayApplication.kt        — Application class, Hilt entry point
  MainActivity.kt                   — Single activity, NavGraph entry, notification permission
  di/
    AppModule.kt                    — OkHttpClient, Gson, DataStore, NotificationManager providers
    NetworkModule.kt                — Retrofit (41x retry), PipedApiService, InvidiousApiService
    DatabaseModule.kt               — Room DB provider
  data/
    api/piped/
      PipedApiService.kt            — 9 endpoints: /streams, /trending, /search, /suggestions, etc.
      PipedDtos.kt                  — StreamItem, Stream, StreamFormat, Subtitle DTOs
    api/invidious/
      InvidiousApiService.kt        — Invidious fallback endpoints
    local/db/
      FreedomPlayDatabase.kt        — Room v1: DownloadEntity, PlaylistEntity, PlaylistVideoEntity
      DownloadDao.kt                — CRUD for downloads
      PlaylistDao.kt                — CRUD for playlists
      DownloadEntity.kt             — video_id PK, title, channelName, thumbnailUrl, filePath, fileSize, quality, downloadStatus, progress, downloadedAt
      PlaylistEntity.kt             — id, name, createdAt
      PlaylistVideoEntity.kt        — id, playlistId, videoId, addedAt
    repository/
      StreamRepository.kt           — Piped primary, Invidious fallback
    model/
      StreamItem.kt                 — Feed/search results (id, title, uploaderName, thumbnailUrl, views, duration)
  domain/
    model/
      Stream.kt                     — Full video details (title, videoStreams, audioStreams, subtitles, relatedStreams)
      StreamFormat.kt               — Stream URL + quality + mimeType
      StreamItem.kt                 — Feed/search result model
      Subtitle.kt                   — Subtitle track metadata
  presentation/
    ui/
      theme/
        Theme.kt                    — AmoledColorScheme, DarkColorScheme, LightColorScheme (ThemeType enum)
      navigation/
        NavGraph.kt                 — Routes: Home, Search, Player/{videoId}, Library, Settings
        Screen.kt                   — Screen sealed class
      components/
        BottomNavBar.kt             — Bottom navigation with Home/Search/Library/Settings tabs
        VideoCard.kt                — Video list item with thumbnail, title, channel, views, duration
        MiniPlayer.kt               — Floating mini player overlay
      screens/
        home/HomeScreen.kt          — Home feed (trending/search), pull-to-refresh, shimmer loading
        search/SearchScreen.kt      — Search bar + suggestions + results
        player/PlayerScreen.kt      — ExoPlayer, quality selector, PiP, fullscreen, share/download
        library/LibraryScreen.kt    — Downloads, playlists, watch history
        settings/SettingsScreen.kt  — Theme, audio, playback, download, account settings
    viewmodel/
      HomeViewModel.kt              — Trending feed, search-based recommendations
      SearchViewModel.kt            — Search query + results + suggestions
      PlayerViewModel.kt            — Video stream loading, quality selection
      LibraryViewModel.kt           — Downloads list management
  download/
    DownloadManager.kt              — WorkManager orchestration: start/pause/cancel downloads
    DownloadWorker.kt               — @HiltWorker: HTTP download + MediaMuxer mux, progress notifications
  util/
    TimeUtils.kt                    — Duration/relative time formatting
    UrlUtils.kt                     — YouTube URL parsing, video ID extraction
```

## Settings

PreferencesManager (DataStore) provides:
- ThemeType (AMOLED/GRADIENT/MATERIAL3/LIGHT)
- Default quality, skip silence, volume normalization, audio only mode
- Remember position, default download quality

## Conventions

- Kotlin, Compose, Material 3
- MVVM with repository pattern
- Hilt dependency injection everywhere
- AMOLED theme: pure black (#000000) backgrounds, hot pink (#FF4081) accents
- Application class: FreedomPlayApplication
- Package: `com.freedomplay.app`
- Deprecated Compose icons use `Icons.AutoMirrored.Filled.*` variants
