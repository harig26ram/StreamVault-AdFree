# Task 5: NetworkModule cookie header injection + getHomeFeed cache-first tier

## Status: DONE

## Changes Made

### Part A: NetworkModule cookie header injection
- `NetworkModule.kt`: Added `CookieStore` parameter to `provideYouTubeOkHttpClient`
- Added `computeSapiSidHash()` companion function with correct SAPISIDHASH format (`timestamp:hash` with dots in input)
- Cookie header injection: when `cookieStore.isConnected` AND request path starts with `/youtubei/v1/browse`, sets `Cookie` + `Authorization: SAPISIDHASH` headers and removes Bearer token
- Added `CookieFeedRepository` binding in `RepositoryModule.kt`

### Part B: VideoRepositoryImpl cookie-ML tier + feed cache
- `VideoRepositoryImpl.kt`: Added `CookieFeedRepository` and `CookieStore` to constructor
- Added `lastGoodFeed: List<FeedItem>?` field for feed caching
- **Tier 0** (new, highest priority): Cookie-ML personalized feed via `cookieFeedRepository.getPersonalizedHomeFeed()`
- **Cache fallback**: Before final failure, returns `lastGoodFeed` if available
- Video→FeedItem mapping: `personalizedItems.map { FeedItem.Video(it) }`

### Part C: Unit tests
- Created `CookieHeaderTest.kt` (4 tests): SAPISIDHASH format, consistency, input verification, custom origin
- Updated `CookieFeedRepositoryTest.kt`: Fixed 3 tests to match new colon format (`:` instead of `_`)

### Part D: Build verification
- `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL

## Test Summary
- **CookieHeaderTest**: 4/4 passed
- **CookieFeedRepositoryTest**: 17/17 passed (3 updated for new format)
- **Full suite**: All tests pass (BUILD SUCCESSFUL)

## Commits
- TBD (pending user request to commit)

## Concerns
- None
