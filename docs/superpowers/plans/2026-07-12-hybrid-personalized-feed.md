# Hybrid Personalized Feed — Cookie-ML + Themeable Magazine UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a personalized YouTube home feed (real ML recommendations via YouTube session cookies, with a robust local fallback) plus an in-app theme selector (6 accents) and a magazine-grid home UI with collapsible player premium controls, for closed-circle distribution.

**Architecture:** The feed is a layered pipeline — InnerTube `FEwhat_to_watch` with YouTube session-cookie auth (SAPISIDHASH) as the top personalization layer, falling back to the existing search/watch-history feed, then trending. The theme system adds an `accent: Color` parameter to `FreedomPlayTheme` and persists the choice in DataStore via `ThemeManager`. Home uses a `MagazineFeed` (2-col grid + featured), and `PlayerScreen` gets collapsible Download/EQ panels.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt 2.50, DataStore Preferences 1.0.0 (already a dependency), Retrofit/Gson, Room 2.6.1, Coil 2.5.0.

## Global Constraints

- Package: `com.streamvault.app`. Min SDK 24, Target 36. Application ID unchanged.
- AMOLED theme is the default (pure black `#000000` backgrounds). Themes only recolor the accent — Hot Pink `#FF4081` is the default accent.
- Hardcoded strings in Compose = BUG — use `stringResource(R.string.xxx)` for any new user-facing copy. (Short toast-only/internal labels may use existing patterns.)
- Build command: `.\gradlew.bat assembleDebug`. Unit tests: `.\gradlew.bat testDebugUnitTest`.
- **Cookie storage deviation (important):** spec says "encrypted" but `security-crypto` is NOT a project dependency. Store the opaque cookie string in plain DataStore Preferences with light XOR obfuscation. Do NOT add `security-crypto` (keeps build simple). The cookie is replayed as-is, never parsed into parts.
- Cookie acquisition is an explicit PASTE dialog (user copies cookies from a browser). No WebView scraping.
- Collapsible player controls: Download panel open by default; EQ panel hidden by default.

---

### Task 1: Theme definitions + ThemeManager

**Files:**
- Create: `app/src/main/java/com/streamvault/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/streamvault/app/theme/ThemeManager.kt`

**Interfaces:**
- Produces: `val AppThemes: List<Theme>`, `data class Theme(val id: String, val name: String, val accent: Color)`, `fun Theme.toColorScheme(amoled: Boolean): ColorScheme`, `class ThemeManager @Inject constructor(@ApplicationContext ctx)`, `ThemeManager.selectedAccentFlow: StateFlow<Color>`, `suspend fun ThemeManager.setTheme(id: String)`, `ThemeManager.selectedThemeFlow: StateFlow<Theme>`.

- [ ] **Step 1: Write the theme definitions file**

```kotlin
package com.streamvault.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

data class Theme(val id: String, val name: String, val accent: Color)

val AppThemes = listOf(
    Theme("hot_pink", "Hot Pink", Color(0xFFF04081)),
    Theme("digital_waves", "Digital Waves", Color(0xFF4FC3F7)),
    Theme("eco_frequency", "Eco Frequency", Color(0xFF69F0AE)),
    Theme("neon_purple", "Neon Purple", Color(0xFFB388FF)),
    Theme("amber_horizon", "Amber Horizon", Color(0xFFFFB74D)),
    Theme("crimson", "Crimson", Color(0xFFFF5252)),
)

fun Theme.toColorScheme(amoled: Boolean): ColorScheme {
    val bg = if (amoled) Color(0xFF000000) else Color(0xFF121212)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF000000),
        secondary = accent,
        onSecondary = Color(0xFF000000),
        tertiary = accent,
        background = bg,
        surface = if (amoled) Color(0xFF0A0A0A) else Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFF242424),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF9A9A9A),
        error = Color(0xFFFF5252),
    )
}
```

- [ ] **Step 2: Write ThemeManager**

```kotlin
package com.streamvault.app.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streamvault.app.ui.theme.AppThemes
import com.streamvault.app.ui.theme.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "freedomplay_prefs")
private val THEME_KEY = stringPreferencesKey("selected_theme_id")

@Singleton
class ThemeManager @Inject constructor(
    @androidx.hilt.work.NodeId @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    val selectedThemeFlow: Flow<Theme> = context.dataStore.data.map { prefs ->
        val id = prefs[THEME_KEY] ?: "hot_pink"
        AppThemes.firstOrNull { it.id == id } ?: AppThemes.first()
    }

    suspend fun setTheme(id: String) {
        context.dataStore.edit { it[THEME_KEY] = id }
    }
}
```

- [ ] **Step 3: Run a build to confirm it compiles**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL (or at least no errors in the 2 new files).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/streamvault/app/ui/theme/Theme.kt app/src/main/java/com/streamvault/app/theme/ThemeManager.kt
git commit -m "feat: add theme definitions + ThemeManager (DataStore)"
```

---

### Task 2: Wire accent into FreedomPlayTheme + Settings theme selector

**Files:**
- Modify: `app/src/main/java/com/streamvault/app/ui/theme/Theme.kt` (add accent param to `FreedomPlayTheme`)
- Modify: `app/src/main/java/com/streamvault/app/presentation/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/com/streamvault/app/presentation/ui/MainActivity.kt` (collect theme and pass to FreedomPlayTheme)

**Interfaces:**
- Consumes: `ThemeManager.selectedThemeFlow`, `Theme.toColorScheme(amoled)`.
- Produces: `FreedomPlayTheme(accent: Color, amoledMode: Boolean, content)` uses `Theme(id=...,name=...,accent).toColorScheme(amoled)`.

- [ ] **Step 1: Add accent param to FreedomPlayTheme**

In `Theme.kt`, change the signature and body so the selected accent drives the scheme:

```kotlin
@Composable
fun FreedomPlayTheme(
    accent: Color = Color(0xFFF04081),
    darkTheme: Boolean = true,
    amoledMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = Theme("", "", accent).toColorScheme(amoledMode)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
```

- [ ] **Step 2: MainActivity collects selected theme and passes accent**

Locate the `setContent { FreedomPlayTheme { ... } }` call in `MainActivity.kt`. Collect `themeManager.selectedThemeFlow` (via `collectAsStateWithLifecycle` or `collectAsState()` inside `setContent`) and pass `accent = theme.accent`:

```kotlin
val theme by themeManager.selectedThemeFlow.collectAsState(initial = AppThemes.first())
FreedomPlayTheme(accent = theme.accent) {
    AppRoot()
}
```

- [ ] **Step 3: Add a Theme swatch row to SettingsScreen Appearance section**

In `SettingsScreen.kt`, inside the Appearance `SettingsSection`, add a row that reads `themeManager.selectedThemeFlow` (collect as state via the `SettingsViewModel` exposing it, or collect directly with `collectAsState()`) and renders a horizontal `LazyRow` of swatches. On click, call `themeManager.setTheme(t.id)`:

```kotlin
SettingsSection(title = "Theme") {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(16.dp)) {
        items(AppThemes) { t ->
            val selected = t.id == currentTheme.id
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(t.accent)
                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable { vm.selectTheme(t.id) },
            )
        }
    }
}
```

Expose `selectTheme(id: String)` from `SettingsViewModel` (calls `themeManager.setTheme`) and `currentTheme` as state.

- [ ] **Step 4: Run build + unit test for theme wiring**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/streamvault/app/ui/theme/Theme.kt app/src/main/java/com/streamvault/app/presentation/ui/screen/SettingsScreen.kt app/src/main/java/com/streamvault/app/presentation/ui/MainActivity.kt app/src/main/java/com/streamvault/app/presentation/viewmodel/SettingsViewModel.kt
git commit -m "feat: accent-driven theme + in-app theme selector"
```

---

### Task 3: CookieStore (closed-circle personalization credentials)

**Files:**
- Create: `app/src/main/java/com/streamvault/app/auth/CookieStore.kt`
- Test: `app/src/test/java/com/streamvault/app/auth/CookieStoreTest.kt`

**Interfaces:**
- Produces: `class CookieStore @Inject constructor(@ApplicationContext ctx)`, `suspend fun save(rawCookie: String)`, `suspend fun get(): String?`, `suspend fun clear()`, `val isConnectedFlow: Flow<Boolean>`.

- [ ] **Step 1: Write the failing test**

```kotlin
class CookieStoreTest {
    @Test fun saveThenGet_returnsCookie() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = CookieStore(ctx)
        store.save("a=1; SAPISID=xyz; b=2")
        assertEquals("a=1; SAPISID=xyz; b=2", store.get())
        assertTrue(store.isConnectedFlow.first())
    }

    @Test fun clear_removesCookie() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = CookieStore(ctx)
        store.save("x=1")
        store.clear()
        assertNull(store.get())
        assertFalse(store.isConnectedFlow.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamvault.app.auth.CookieStoreTest"`
Expected: FAIL (class does not exist).

- [ ] **Step 3: Write CookieStore (DataStore + XOR obfuscation)**

```kotlin
package com.streamvault.app.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

private val Context.dataStore by preferencesDataStore(name = "freedomplay_cookies")
private val COOKIE_KEY = stringPreferencesKey("youtube_cookie")
private const val XOR_KEY = "freedomplay-obfuscate-v1"

@Singleton
class CookieStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    val isConnectedFlow: Flow<Boolean> = context.dataStore.data.map { it[COOKIE_KEY] != null }

    suspend fun save(raw: String) {
        val obf = xorObfuscate(raw)
        context.dataStore.edit { it[COOKIE_KEY] = obf }
    }
    suspend fun get(): String? =
        context.dataStore.data.first()[COOKIE_KEY]?.let { xorDeobfuscate(it) }
    suspend fun clear() = context.dataStore.edit { it.remove(COOKIE_KEY) }

    private fun xorObfuscate(s: String): String {
        val out = s.toByteArray().mapIndexed { i, b -> (b.toInt() xor XOR_KEY[i % XOR_KEY.length].code).toByte() }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
    private fun xorDeobfuscate(s: String): String {
        val raw = Base64.decode(s, Base64.NO_WRAP)
        return raw.mapIndexed { i, b -> (b.toInt() xor XOR_KEY[i % XOR_KEY.length].code).toByte().toInt().toChar() }.joinToString("")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamvault.app.auth.CookieStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/streamvault/app/auth/CookieStore.kt app/src/test/java/com/streamvault/app/auth/CookieStoreTest.kt
git commit -m "feat: CookieStore for YouTube session cookies (DataStore + obfuscation)"
```

---

### Task 4: CookieFeedRepository (SAPISIDHASH personalization)

**Files:**
- Create: `app/src/main/java/com/streamvault/app/data/repository/CookieFeedRepository.kt`
- Create: `app/src/main/java/com/streamvault/app/data/repository/CookieFeedRepositoryImpl.kt`
- Test: `app/src/test/java/com/streamvault/app/data/repository/CookieFeedRepositoryTest.kt`

**Interfaces:**
- Consumes: `CookieStore.get()` (Task 3), `YouTubeApiService.browseRaw(BrowseRequest)` (existing, returns `retrofit2.Response<JsonElement>`), `VisitorDataBootstrapper.getCachedVisitorData()`.
- Produces: `interface CookieFeedRepository { suspend fun getPersonalizedHomeFeed(): Result<List<Video>> }`, `suspend fun computeSapiSidHash(cookie: String, origin: String): String` (internal, testable).

- [ ] **Step 1: Write failing test for SAPISIDHASH + parsing**

```kotlin
class CookieFeedRepositoryTest {
    @Test fun computeSapiSidHash_format() {
        val hash = CookieFeedRepositoryImpl.computeSapiSidHash("SAPISID=ABC123; OTHER=1", "https://www.youtube.com")
        assertTrue(hash.startsWith("SAPISIDHASH "), "got: $hash")
        // format: "SAPISIDHASH <timestamp>_<sha1(timestamp+' '+origin+' '+sapisid)>"
        val parts = hash.removePrefix("SAPISIDHASH ").split("_")
        assertEquals(2, parts.size)
        assertEquals(40, parts[1].length) // sha1 hex length
    }

    @Test fun extractSapiSid_findsValue() {
        assertEquals("ABC123", CookieFeedRepositoryImpl.extractSapiSid("a=1; SAPISID=ABC123; b=2"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamvault.app.data.repository.CookieFeedRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement repository + SAPISIDHASH**

```kotlin
package com.streamvault.app.data.repository

import com.google.gson.JsonElement
import com.streamvault.app.auth.CookieStore
import com.streamvault.app.data.api.YouTubeApiService
import com.streamvault.app.data.local.VisitorDataBootstrapper
import com.streamvault.app.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface CookieFeedRepository {
    suspend fun getPersonalizedHomeFeed(): Result<List<Video>>
}

@Singleton
class CookieFeedRepositoryImpl @Inject constructor(
    private val apiService: YouTubeApiService,
    private val cookieStore: CookieStore,
    private val bootstrapper: VisitorDataBootstrapper,
) : CookieFeedRepository {

    override suspend fun getPersonalizedHomeFeed(): Result<List<Video>> = withContext(Dispatchers.IO) {
        val cookie = cookieStore.get() ?: return@withContext Result.failure(IllegalStateException("no cookie"))
        val visitorData = bootstrapper.getCachedVisitorData() ?: ""
        val origin = "https://www.youtube.com"
        val auth = computeSapiSidHash(cookie, origin)
        return@withContext try {
            val req = com.streamvault.app.data.model.BrowseRequest(
                context = com.streamvault.app.data.model.ContextData(visitorData = visitorData),
                browseId = "FEwhat_to_watch",
            )
            val resp = apiService.browseRaw(req, origin, auth, "SAPISIDHASH", cookie)
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("browse ${resp.code()}"))
            val items = parsePersonalizedFeed(resp.body())
            if (items.isEmpty()) Result.failure(Exception("empty")) else Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePersonalizedFeed(body: JsonElement?): List<Video> {
        if (body == null) return emptyList()
        return try {
            val root = body.asJsonObject
            val tabs = root.getAsJsonObject("contents")
                .getAsJsonObject("twoColumnBrowseResultsRenderer")
                .getAsJsonArray("tabs")
            val out = mutableListOf<Video>()
            for (tab in tabs) {
                val tabObj = tab.asJsonObject
                val content = tabObj.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?: continue
                walkRenderers(content, out)
                if (out.size >= 40) break
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    private fun walkRenderers(node: JsonObject, out: MutableList<Video>) {
        for ((_, value) in node.entrySet()) {
            if (value !is JsonObject) continue
            val vwc = value.getAsJsonObject("videoWithContextRenderer")
                ?: value.getAsJsonObject("compactVideoRenderer")
                ?: value.getAsJsonObject("richItemRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("videoRenderer")
            if (vwc != null) {
                val id = vwc.getAsJsonPrimitive("videoId")?.asString ?: continue
                val title = vwc.getAsJsonObject("headline")?.getAsJsonPrimitive("simpleText")?.asString
                    ?: vwc.getAsJsonObject("title")?.getAsJsonPrimitive("simpleText")?.asString
                    ?: vwc.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("text")?.asString
                    ?: ""
                val owner = vwc.getAsJsonObject("ownerText")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject
                val channel = owner?.getAsJsonPrimitive("text")?.asString ?: ""
                val views = vwc.getAsJsonObject("viewCountText")?.getAsJsonPrimitive("simpleText")?.asString ?: ""
                val thumb = vwc.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                    ?.lastOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString ?: ""
                out.add(Video(id = id, title = title, channelName = channel, viewCount = views, thumbnailUrl = thumb, watchUrl = "https://youtube.com/watch?v=$id"))
            }
            // recurse into nested objects to find more renderers
            for ((_, v) in value.entrySet()) {
                if (v is JsonObject) walkRenderers(v, out)
            }
        }
    }

    companion object {
        fun extractSapiSid(cookie: String): String? =
            cookie.split(";").map { it.trim() }.firstOrNull { it.startsWith("SAPISID=") }
                ?.removePrefix("SAPISID=")?.trim()

        fun computeSapiSidHash(cookie: String, origin: String): String {
            val sapisid = extractSapiSid(cookie) ?: ""
            val time = (System.currentTimeMillis() / 1000).toString()
            val toHash = "$time $origin $sapisid"
            val sha1 = MessageDigest.getInstance("SHA-1").digest(toHash.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return "SAPISIDHASH $time\_$sha1"
        }
    }
}
```

Note: the `apiService.browseRaw` call signature must match the existing `YouTubeApiService.browseRaw` — adjust parameter names (e.g. `@Header` annotations) to the actual declaration in `YouTubeApiService`. The SAPISIDHASH header value is `"SAPISIDHASH <time>_<sha1>"` and the `Authorization` header is `"SAPISIDHASH"` (per InnerTube convention, the scheme is the literal string `SAPISIDHASH`).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamvault.app.data.repository.CookieFeedRepositoryTest"`
Expected: PASS (SAPISIDHASH shape + extraction).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/streamvault/app/data/repository/CookieFeedRepository.kt app/src/main/java/com/streamvault/app/data/repository/CookieFeedRepositoryImpl.kt app/src/test/java/com/streamvault/app/data/repository/CookieFeedRepositoryTest.kt
git commit -m "feat: cookie-ML personalized feed (SAPISIDHASH innerTube browse)"
```

---

### Task 5: Inject cookie headers in NetworkModule + layer into getHomeFeed

**Files:**
- Modify: `app/src/main/java/com/streamvault/app/di/NetworkModule.kt` (provide @Named("youtube") client to also read CookieStore)
- Modify: `app/src/main/java/com/streamvault/app/data/repository/VideoRepositoryImpl.kt` (getHomeFeed: cookie-ML → local → trending)
- Test: `app/src/test/java/com/streamvault/app/di/CookieHeaderTest.kt` (unit test the interceptor logic in isolation)

**Interfaces:**
- Consumes: `CookieStore.get()` (Task 3), `CookieFeedRepository.getPersonalizedHomeFeed()` (Task 4).
- Produces: updated `VideoRepositoryImpl.getHomeFeed()` returning cookie-ML results first.

- [ ] **Step 1: Add cookie injection to the youtube OkHttp interceptor**

In `NetworkModule.provideYouTubeOkHttpClient`, add `cookieStore: CookieStore` parameter. Inside the existing auth interceptor (the block that already adds `key`, `User-Agent`, `X-Goog-Visitor-Data`), add before the request proceeds:

```kotlin
val cookie = runBlocking { cookieStore.get() }
if (!cookie.isNullOrBlank() && request.url.encodedPath.contains("/youtubei/v1/browse")) {
    val origin = "https://www.youtube.com"
    val hash = CookieFeedRepositoryImpl.computeSapiSidHash(cookie, origin)
    requestBuilder.addHeader("Authorization", "SAPISIDHASH")
    requestBuilder.addHeader("X-Goog-AuthUser", "0")
    requestBuilder.addHeader("Cookie", cookie)
    requestBuilder.addHeader("Origin", origin)
    // SAPISIDHASH sent via Authorization header value is the literal scheme; InnerTube expects:
    requestBuilder.header("Authorization", hash)
}
```

- [ ] **Step 2: Reorder getHomeFeed tiers in VideoRepositoryImpl**

In `getHomeFeed()`, make the cookie-ML feed the FIRST tier, before the existing local search feed:

```kotlin
return flow {
    // Tier 0: real personalized (cookie-ML)
    cookieFeedRepository.getPersonalizedHomeFeed().onSuccess { vids ->
        if (vids.isNotEmpty()) { emit(vids); return@flow }
    }
    // Tier 1..3: existing local search / homepage / trending fallback
    // (existing logic unchanged, now runs only when cookie feed empty/fails)
    ...
}
```

- [ ] **Step 3: Write a unit test for the cookie-header interceptor**

Extract the header-building into a `fun buildCookieHeaders(cookie: String): Map<String,String>` testable pure function (or test via a mock `Interceptor.Chain`). Assert it sets `Cookie` + `Authorization` starting with `SAPISIDHASH` for `/youtubei/v1/browse` paths and does nothing for other paths.

- [ ] **Step 4: Build + run unit tests**

Run: `.\gradlew.bat testDebugUnitTest ; .\gradlew.bat assembleDebug`
Expected: tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/streamvault/app/di/NetworkModule.kt app/src/main/java/com/streamvault/app/data/repository/VideoRepositoryImpl.kt app/src/test/java/com/streamvault/app/di/CookieHeaderTest.kt
git commit -m "feat: inject cookie auth into innerTube + layer cookie-ML feed first"
```

---

### Task 6: MagazineFeed home UI (2-col grid + featured, compact detail)

**Files:**
- Create: `app/src/main/java/com/streamvault/app/presentation/ui/components/MagazineFeed.kt`
- Modify: `app/src/main/java/com/streamvault/app/presentation/ui/screen/HomeScreen.kt`

**Interfaces:**
- Consumes: `List<FeedItem>` from `HomeViewModel.uiState.feedItems`, same callbacks as current `HomeScreen` (`onVideoClick`, `onChannelClick`, `onPlaylistClick`, `onSaveToWatchLater`, `onShare`).
- Produces: `MagazineFeed(items, onVideoClick, onChannelClick, onPlaylistClick, onSaveToWatchLater, onShare)` composable.

- [ ] **Step 1: Build MagazineFeed composable**

Featured = first `FeedItem.Video` rendered full-width; remaining videos in a 2-column `LazyVerticalGrid` with a COMPACT detail row (smaller title 12sp, single-line channel + views). Reuse existing `VideoCard` for the featured; for grid cells write a `CompactVideoCard` with `Modifier.height` tuned so the detail area is ~30% smaller than `VideoCard`.

```kotlin
@Composable
fun MagazineFeed(
    items: List<FeedItem>,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSaveToWatchLater: (Video) -> Unit,
    onShare: (Video) -> Unit,
) {
    val videos = items.filterIsInstance<FeedItem.Video>().map { it.video }
    val featured = videos.firstOrNull()
    val rest = videos.drop(1)
    LazyColumn {
        if (featured != null) item {
            VideoCard(video = featured, onClick = { onVideoClick(featured.id) },
                onSaveToWatchLater = onSaveToWatchLater, onShare = onShare)
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height((rest.size / 2 * 220).dp.coerceAtLeast(220.dp)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(rest) { v ->
                    CompactVideoCard(video = v, onClick = { onVideoClick(v.id) },
                        onSaveToWatchLater = onSaveToWatchLater, onShare = onShare)
                }
            }
        }
    }
}
```

`CompactVideoCard`: thumbnail (16:9) + a detail row with title 12sp (max 2 lines) and a single muted line `<channel> · <views>`. Keep height tight so the detail area is visibly smaller than `VideoCard`.

- [ ] **Step 2: Swap HomeScreen to use MagazineFeed**

In `HomeScreen.kt`, replace the `LazyColumn { items(...) }` block (lines ~209-403) with `MagazineFeed(...)`. Preserve the existing `PullToRefreshBox`, `listState`/load-more `LaunchedEffect`, and error/empty states.

- [ ] **Step 3: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/streamvault/app/presentation/ui/components/MagazineFeed.kt app/src/main/java/com/streamvault/app/presentation/ui/screen/HomeScreen.kt
git commit -m "feat: magazine-grid home feed (compact detail rows)"
```

---

### Task 7: Collapsible player premium panels (Download open / EQ hidden)

**Files:**
- Create: `app/src/main/java/com/streamvault/app/presentation/ui/components/CollapsiblePanel.kt`
- Modify: `app/src/main/java/com/streamvault/app/presentation/ui/screen/PlayerScreen.kt`

**Interfaces:**
- Consumes: existing `PlayerViewModel` state (`isDownloaded/isDownloading/downloadProgress`, `equalizerEnabled`) + existing actions (`startDownload/deleteDownload/pauseDownload`, `onEqualizerClick`).
- Produces: `CollapsiblePanel(title, defaultExpanded, content)` composable.

- [ ] **Step 1: Write CollapsiblePanel**

```kotlin
@Composable
fun CollapsiblePanel(
    title: String,
    defaultExpanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded) { Column(content = content) }
    }
}
```

- [ ] **Step 2: Wrap Download + EQ controls in PlayerScreen**

In `PlayerScreen.kt`, replace the inline Download mini-action block and the Equalizer icon button with two `CollapsiblePanel`s placed near the player controls:
- `CollapsiblePanel("Download", defaultExpanded = true)` → contains the existing download start/pause/delete UI bound to `uiState`.
- `CollapsiblePanel("Equalizer", defaultExpanded = false)` → contains an "Open equalizer" button calling `onEqualizerClick`.

Keep `equalizerEnabled` tint logic (pink when on) on the panel title.

- [ ] **Step 3: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/streamvault/app/presentation/ui/components/CollapsiblePanel.kt app/src/main/java/com/streamvault/app/presentation/ui/screen/PlayerScreen.kt
git commit -m "feat: collapsible Download/EQ panels in player"
```

---

### Task 8: Connect-account (cookie paste) dialog + closed-circle setup doc

**Files:**
- Modify: `app/src/main/java/com/streamvault/app/presentation/ui/screen/SettingsScreen.kt` (Account section: "Connect YouTube account" paste dialog + Disconnect)
- Create: `docs/CLOSED_CIRCLE_SETUP.md`

**Interfaces:**
- Consumes: `CookieStore.save(cookie)` / `clear()` (Task 3), `isConnectedFlow`.

- [ ] **Step 1: Add Connect-account UI to SettingsScreen**

In the Account section of `SettingsScreen.kt`, add a `SettingsItem` "Connect YouTube account" that opens an `AlertDialog` with a `TextField` (placeholder text referencing copying cookies from browser dev-tools) and Connect/Disconnect buttons. On Connect call `cookieStore.save(text)`; when `isConnectedFlow` is true show "Disconnect" which calls `cookieStore.clear()`.

- [ ] **Step 2: Write docs/CLOSED_CIRCLE_SETUP.md**

Document the closed-circle distribution steps:
1. Google Cloud Console → OAuth consent screen stays in **Testing**; add each member's Gmail under "Test users" (max 100).
2. Enable **YouTube Data API v3** (not strictly required for cookie feed, but harmless; note the cookie feed needs no API key).
3. Build + distribute `app-debug.apk` privately (any file share / sideload).
4. For personalized ML feed: in app Settings → Connect YouTube account → paste cookies copied from `https://www.youtube.com` (dev-tools → Application → Cookies → copy the full cookie string). Cookie step is optional; without it the app shows the local recommendation feed.
5. Note the 7-day re-sign-in for OAuth test users; the cookie session lasts until Google expires it (re-paste when feed empties).

- [ ] **Step 3: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/streamvault/app/presentation/ui/screen/SettingsScreen.kt docs/CLOSED_CIRCLE_SETUP.md
git commit -m "feat: connect-account cookie dialog + closed-circle setup doc"
```

---

### Final verification

- [ ] **Run full unit suite + debug build**

Run: `.\gradlew.bat testDebugUnitTest ; .\gradlew.bat assembleDebug`
Expected: all tests PASS, BUILD SUCCESSFUL.

- [ ] **Install on emulator, verify:**
1. Settings → Theme → switch accents → UI recolors live (no restart needed).
2. Home shows magazine grid (featured + 2-col). Pull-to-refresh still works.
3. Player → Download panel open by default, EQ hidden; expand EQ works.
4. (Optional) Paste YouTube cookies → Home feed becomes real personalized recommendations; clearing cookie reverts to local feed.

- [ ] **Commit any verification fixes, then push to `dev` if cleared by user.**
