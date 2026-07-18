# Fix: Home Feed Returns 0 Items (YouTube Format Change)

## Context

FreedomPlay's home feed returns 0 items because YouTube changed their InnerTube response format. The `richGridRenderer.contents[]` array now wraps items in `{ richSectionRenderer: { content: { richShelfRenderer: { contents: [...] } } } }`. The current code handles `richSectionRenderer` but NOT `richShelfRenderer` inside it, so the recursion chain breaks and 0 videos are extracted. This also causes 36+ skipped frames from 6 sequential network fallbacks all returning empty.

## Files to Modify

1. `app/src/main/java/com/streamvault/app/data/repository/VideoRepositoryImpl.kt`
2. `app/src/main/java/com/streamvault/app/data/repository/CookieFeedRepository.kt`

## Changes

### Fix 1: Add `richShelfRenderer` handler to `extractVideosFromJson()` (VideoRepositoryImpl.kt ~line 821)

After the `shelfRenderer` block (line 821), add:

```kotlin
// Rich shelf renderer (YouTube 2024+ format inside richSectionRenderer)
obj.getAsJsonObject("richShelfRenderer")
    ?.getAsJsonArray("contents")?.forEach { shelfItem ->
        extractVideosFromJson(shelfItem.asJsonObject, items)
    }
```

### Fix 2: Make `richItemRenderer` content extraction generic (VideoRepositoryImpl.kt line 793-797)

Replace the current narrow extraction:
```kotlin
obj.getAsJsonObject("richItemRenderer")
    ?.getAsJsonObject("content")
    ?.getAsJsonObject("videoRenderer")?.let { renderer ->
        items.add(FeedItem.Video(jsonToVideo(renderer)))
    }
```

With generic recursion:
```kotlin
obj.getAsJsonObject("richItemRenderer")
    ?.getAsJsonObject("content")?.let { content ->
        extractVideosFromJson(content, items)
    }
```

This ensures any renderer type inside `richItemRenderer.content` is handled (videoRenderer, reelItemRenderer, etc.).

### Fix 3: Add `playlistShelfRenderer` handler (VideoRepositoryImpl.kt ~line 821)

After the `richShelfRenderer` block:
```kotlin
// Playlist shelf renderer
obj.getAsJsonObject("playlistShelfRenderer")
    ?.getAsJsonArray("contents")?.forEach { shelfItem ->
        extractVideosFromJson(shelfItem.asJsonObject, items)
    }
```

### Fix 4: Add continuation token extraction from `richGridRenderer` (VideoRepositoryImpl.kt ~line 695)

Extend `extractContinuationToken()` or add a new call after it to walk `richGridRenderer.contents` for `continuationItemRenderer`:

```kotlin
// Also search richGridRenderer for embedded continuation tokens
if (continuationToken == null) {
    root?.getAsJsonObject("contents")
        ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
        ?.getAsJsonArray("tabs")?.forEach { tab ->
            val tabContent = tab.asJsonObject?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")
            val richGrid = tabContent?.getAsJsonObject("richGridRenderer")
            richGrid?.getAsJsonArray("contents")?.forEach { item ->
                val contToken = item.asJsonObject
                    ?.getAsJsonObject("continuationItemRenderer")
                    ?.getAsJsonObject("continuationEndpoint")
                    ?.getAsJsonObject("continuationCommand")
                    ?.get("token")?.asString
                if (contToken != null && continuationToken == null) {
                    continuationToken = contToken
                }
            }
        }
}
```

### Fix 5: Apply same fixes to `CookieFeedRepository.kt` (lines 120-165)

In `extractVideosRecursive()`:
- Add `richShelfRenderer` handler (same as Fix 1)
- Make `richItemRenderer` content extraction generic (same as Fix 2)
- Add `playlistShelfRenderer` handler (same as Fix 3)

### Fix 6: Fix misleading debug log (VideoRepositoryImpl.kt line 675)

Change:
```kotlin
Log.d(TAG, "    richItem keys: ${itemObj.keySet()}")
```
To:
```kotlin
Log.d(TAG, "    richGrid item keys: ${itemObj.keySet()}")
```

## Verification

1. Build: `.\gradlew.bat assembleDebug` — must compile cleanly
2. Unit tests: `.\gradlew.bat testDebugUnitTest` — all 192 tests must pass
3. Deploy to OnePlus device and verify home feed loads videos
4. Check logcat for `parseBrowse: total items > 0`
