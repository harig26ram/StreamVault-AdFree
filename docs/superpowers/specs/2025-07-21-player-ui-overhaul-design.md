# FreedomPlay UI Overhaul Design Spec

**Date:** 2025-07-21
**Status:** DRAFT - Awaiting User Review
**Scope:** 5 interconnected issues

---

## Issue 1: Ads in Related Videos List

### Problem
When selecting a video from the LazyColumn below the player, an ad plays instead of the video. Search results and home feed work correctly.

### Root Cause Analysis
- Related videos come from `StreamRepository.getRelatedStreams(videoId)` → YouTube InnerTube `next` endpoint
- The response may contain "promoted" or "AD" items in the `results` array
- These ad items have different video IDs that don't resolve to playable streams
- When user clicks an ad item, it loads the ad's video ID (which may be an ad-only ID)

### Solution
**Filter ads from API response:**

```kotlin
// In StreamRepository.kt - getRelatedStreams()
fun getRelatedStreams(videoId: String): List<StreamItem> {
    // ... existing fetch logic ...
    
    // Filter out ads/promoted content
    return rawResults.filter { item ->
        // Items with these markers are ads
        item.title?.contains("Ad · ") == false &&
        item.badges?.any { badge -> 
            badge.text == "AD" || badge.text == "SPONSORED" 
        } != true &&
        item.trackingParams != null && // ads may have null tracking
        item.viewCount >= 0 // ads may have negative view counts
    }
}
```

**Additional measures:**
- Log filtered ad items for debugging
- Add fallback: if filtered list is empty, show loading state instead of empty ad
- Consider using `getStream()` on the actual video ID instead of relying solely on `getRelatedStreams()` response

---

## Issue 2: Music Tab - Full YouTube Music Clone (3-Tab)

### Design: 3-Tab Music Home

**Tab Structure:**
```
┌─────────────────────────────────────┐
│  [Home]  [Explore]  [Library]       │  ← Music-specific bottom nav
├─────────────────────────────────────┤
│  Content based on selected tab      │
│  - Home: Personalized mixes,        │
│    recommended, recently played     │
│  - Explore: Charts, genres, moods,  │
│    new releases                     │
│  - Library: Playlists, albums,      │
│    songs, artists                   │
└─────────────────────────────────────┘
```

### Component Breakdown

**1. MusicHomeScreen.kt (Home Tab)**
- Carousel section: "Your Mix", "Listen Again", "Mixed for You"
- Horizontal `LazyRow` of `MusicMixCard` per section
- Recently played section (from Room DB)
- Recommended playlists (from InnerTube)
- Mood/Genre grid (2-column `LazyVerticalGrid`)

**2. MusicExploreScreen.kt (Explore Tab)**
- Charts section: Top 50, Viral 50 (from `getMusicExplore()` → `FEmusic_charts`)
- Genres/Moods: `MoodGenreChip` grid (horizontal rows, 2 columns)
- New Releases: horizontal scrolling list
- Podcasts section (optional, disabled initially)

**3. MusicLibraryScreen.kt (Library Tab)**
- Reuse existing `LibraryScreen.kt` downloads section
- Add: Playlists, Albums, Songs, Artists tabs within Library
- Auth-gated: Show login prompt if not authenticated

### Data Layer Changes

**StreamRepository.kt additions:**
```kotlin
suspend fun getMusicHome(): MusicHomeFeed {
    // Existing: WEB_REMIX InnerTube FEmusic_home
    // Parse into: personalMixes, recentlyPlayed, recommended, moods
}

suspend fun getMusicExplore(): MusicExploreFeed {
    // Existing: WEB_REMIX InnerTube FEmusic_explore + FEmusic_charts
    // Parse into: charts, genres, moods, newReleases
}

suspend fun getMusicLibrary(): MusicLibraryFeed {
    // WEB_REMIX InnerTube FEmusic_library
    // Parse into: playlists, albums, songs, artists
}
```

**New Domain Models:**
```kotlin
data class MusicHomeFeed(
    val personalMixes: List<MusicMix>,
    val recentlyPlayed: List<StreamItem>,
    val recommendedPlaylists: List<StreamItem>,
    val moodsAndGenres: List<MoodGenre>
)

data class MusicExploreFeed(
    val charts: List<MusicChart>,
    val genres: List<MoodGenre>,
    val newReleases: List<StreamItem>
)

data class MusicLibraryFeed(
    val playlists: List<PlaylistEntity>,
    val albums: List<StreamItem>,
    val songs: List<StreamItem>,
    val artists: List<StreamItem>
)
```

### Navigation Integration
- Add `MusicHome`, `MusicExplore`, `MusicLibrary` routes to `Screen.kt`
- Add `MusicTabRow` composable (similar to `BottomNavBar` but for music tabs)
- Music tab in `BottomNavBar` → navigates to `MusicHome` → 3-tab sub-navigation

---

## Issue 3: Player UI - Floating Mini-Player

### Design: YouTube-Style Layout

```
┌─────────────────────────────────────────┐
│  ┌───────────────────────────────────┐  │
│  │         HD VIDEO PLAYER           │  │  ← Pinned at top, 240dp height
│  │    (WebView, ad-blocking JS)      │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │  ← LazyColumn, scrolls UNDER player
│  │  Title · Channel · Views          │  │
│  ├───────────────────────────────────┤  │
│  │  Like · Dislike · Share · Download│  │  ← Action bar
│  ├───────────────────────────────────┤  │
│  │  ▶━━━━━━━━━━━━━━━━━━━━━ 3:42     │  │  ← SeekBar
│  ├───────────────────────────────────┤  │
│  │  Description (collapsible)        │  │
│  ├───────────────────────────────────┤  │
│  │  Related Videos (LazyRow)         │  │  ← NEW: Horizontal feed
│  ├───────────────────────────────────┤  │
│  │  Related Videos (Vertical List)   │  │  ← Ad-filtered
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐  ← Persistent mini-player
│  [Thumbnail] Title · ▶ Play  [✕ Close] │  ← Shows on all screens
└─────────────────────────────────────────┘
```

### Implementation

**PlayerScreen.kt changes:**
- Wrap `HdWebPlayer` in `Box(Modifier.fillMaxWidth().height(240.dp))` at top
- `LazyColumn` gets `Modifier.padding(top = 256.dp)` to scroll under player
- Add `RelatedVideosSection` component (LazyRow + LazyColumn)
- Remove `QualitySelector` and `PlaybackSpeedSelector` from LazyColumn

**MiniPlayerOverlay.kt (new):**
- Located at `MainActivity.kt` level, above `NavHost`
- Composable: `Box(Modifier.fillMaxWidth().height(64.dp))` with `AnimatedVisibility`
- Content: thumbnail, title, play/pause button, expand button, close button
- Visibility controlled by `PlayerViewModel.isMiniPlayerVisible`
- Tap expand → navigate to `PlayerScreen/{videoId}`
- Persist across screens via shared ViewModel at Activity level

**ViewModel Changes:**
```kotlin
// PlayerViewModel.kt
@HiltViewModel
class PlayerViewModel @Inject constructor(...) : ViewModel() {
    private val _currentVideo = MutableStateFlow<Stream?>(null)
    val currentVideo = _currentVideo.asStateFlow()
    
    val isMiniPlayerVisible: StateFlow<Boolean> = _currentVideo.map { it != null }
    
    fun loadVideo(videoId: String) {
        viewModelScope.launch {
            _currentVideo.value = repository.getStream(videoId)
            // Load related videos
            _relatedVideos.value = repository.getRelatedStreams(videoId)
        }
    }
    
    fun minimize() { /* minimize to mini-player */ }
    fun expand() { /* expand to full player */ }
    fun closePlayer() { _currentVideo.value = null }
}
```

---

## Issue 4: Remove Native Quality/Speed Buttons; Always HD

### Design

**Remove from PlayerScreen.kt LazyColumn:**
- `QualitySelector` dropdown (lines ~450-500)
- `PlaybackSpeedSelector` (lines ~500-550)

**HdWebPlayer HD Behavior:**
- Already loads `m.youtube.com/watch?v=` which uses YouTube's adaptive player
- `FOCUS_PLAYER_JS` already forces `player.loadVideoById()` and sets `player.unMute()`
- YouTube's native quality gear icon in WebView UI handles quality selection
- No custom quality UI needed - YouTube's UI is sufficient

**ExoPlayer Fallback (360p):**
- Keep speed control for ExoPlayer fallback path (used for audio-only or when WebView fails)
- ExoPlayer speed selector: `PlaybackSpeedSelector` stays but only in ExoPlayer mode

**Speed Control for WebView:**
- YouTube's native player has speed control in its menu
- No custom speed UI needed for WebView path

---

## Issue 5: Unified UI Wiring - YouTube Functions

### Action Bar Design

**PlayerScreen Action Row:**
```
┌────────────────────────────────────────────┐
│  [👍 Like] [👎 Dislike] [↗ Share] [⬇ Download] │  ← Primary actions
│  [📋 Playlist] [📺 Cast] [🔔 Subscribe]        │  ← Secondary actions
└────────────────────────────────────────────┘
```

### Action Implementations

| Action | Implementation | Auth Required |
|--------|---------------|---------------|
| **Like** | InnerTube `like/like` endpoint with SAPISIDHASH | Yes |
| **Dislike** | InnerTube `like/dislike` endpoint with SAPISIDHASH | Yes |
| **Share** | `Intent.ACTION_SEND` with YouTube URL | No |
| **Download** | `DownloadManager.startDownload()` → Room DB → WorkManager | No |
| **Add to Playlist** | `PlaylistDialog` → Room DB `PlaylistVideoEntity` | No |
| **Cast** | Media3 `CastPlayer` + `CastContext` (basic setup) | No |
| **Subscribe** | InnerTube `subscription/subscribe` with SAPISIDHASH | Yes |

### PlayerActionsUseCase (new)

```kotlin
@Singleton
class PlayerActionsUseCase @Inject constructor(
    private val streamRepository: StreamRepository,
    private val playlistDao: PlaylistDao,
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context
) {
    suspend fun likeVideo(videoId: String): Result<Unit> {
        return streamRepository.likeVideo(videoId)
    }
    
    suspend fun dislikeVideo(videoId: String): Result<Unit> {
        return streamRepository.dislikeVideo(videoId)
    }
    
    fun shareVideo(videoId: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$videoId")
        }
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }
    
    suspend fun downloadVideo(videoId: String, quality: String): Result<Unit> {
        return downloadManager.startDownload(videoId, quality)
    }
    
    suspend fun addToPlaylist(videoId: String, playlistId: Long): Result<Unit> {
        playlistDao.insert(PlaylistVideoEntity(
            playlistId = playlistId,
            videoId = videoId,
            addedAt = System.currentTimeMillis()
        ))
        return Result.success(Unit)
    }
    
    suspend fun subscribe(channelId: String): Result<Unit> {
        return streamRepository.subscribeChannel(channelId)
    }
}
```

### Auth Flow for Like/Subscribe

```kotlin
// In PlayerScreen.kt
if (isAuthenticated) {
    // Show like/dislike/subscribe buttons
    LikeButton(onClick = { viewModel.likeVideo(videoId) })
    SubscribeButton(onClick = { viewModel.subscribe(channelId) })
} else {
    // Show login prompt
    LoginPromptButton(onClick = { showLoginDialog() })
}
```

---

## Architecture Summary

### New Files
- `MusicHomeScreen.kt` - 3-tab music home
- `MusicExploreScreen.kt` - Music explore/charts
- `MusicLibraryScreen.kt` - Music library
- `MusicCarousel.kt` - Reusable carousel component
- `MiniPlayerOverlay.kt` - Persistent mini-player
- `PlayerActionsUseCase.kt` - Unified action handling
- `RelatedVideosSection.kt` - Related videos component

### Modified Files
- `PlayerScreen.kt` - Remove quality/speed selectors, add related videos, mini-player support
- `StreamRepository.kt` - Filter ads from related videos, add like/subscribe endpoints
- `HomeScreen.kt` - Wire Music tab to `MusicHomeScreen`
- `NavGraph.kt` - Add music routes, mini-player overlay
- `PlayerViewModel.kt` - Add mini-player state, action methods
- `Screen.kt` - Add music routes

### Data Flow
```
User taps video
    → PlayerViewModel.loadVideo(videoId)
        → StreamRepository.getStream(videoId) → HdWebPlayer loads m.youtube.com
        → StreamRepository.getRelatedStreams(videoId) → Filter ads → Show in LazyColumn
        → MiniPlayerOverlay shows (video is active)
    → User scrolls down → Related videos visible
    → User taps related video → loadVideo(newVideoId)
    → User taps Like → PlayerActionsUseCase.likeVideo() → InnerTube API
    → User taps Share → PlayerActionsUseCase.shareVideo() → Android Intent
    → User taps Download → PlayerActionsUseCase.downloadVideo() → DownloadManager
```

---

## Questions & Assumptions

### Assumptions
- InnerTube `next` endpoint returns related videos with ad markers we can filter
- WEB_REMIX InnerTube provides enough data for full music home (Home/Explore/Library)
- Media3 Cast support can be added without major refactoring
- SAPISIDHASH auth flow already works for like/subscribe endpoints

### Open Questions
- Cast: Should we add Cast receiver app setup now or defer? (User said "similar to YT")
- Music Library: Should it sync with YouTube Music library or just show local downloads?

### Deferred
- Picture-in-Picture: Already exists in `PlayerScreen.kt`, no changes needed
- Background playback: Media3 already handles this, no changes needed
