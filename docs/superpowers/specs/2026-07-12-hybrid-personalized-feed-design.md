# Hybrid Personalized Feed + All-Account Login — Design Spec

**Date:** 2026-07-12
**Status:** Approved (design), pending implementation plan
**Author:** FreedomPlay / StreamVault-AdFree

## Goal

1. Give the user a **personalized home feed** driven by their Google account, like the real YouTube feed — implemented as a **Hybrid**: official YouTube Data API v3 subscriptions feed blended with the existing local watch-history/search recommendations.
2. Enable **all Google accounts** to sign in (not just the ~100 test users allowed in OAuth "Testing" mode), with robust graceful-degradation methods so the app never hard-breaks for unverified/blocked accounts.
3. Make the app **verification-ready** and deliver the Google Cloud Console + privacy-policy + demo-video package so the user can submit for OAuth verification.

## Core Principle: Graceful Degradation

The **local layer always produces a feed** (no auth needed). The **official layer enhances** it when the OAuth token works. If sign-in fails, the account isn't verified, or Data API quota is exhausted → the app silently falls back to local-only. Nothing ever hard-breaks. The official subscriptions feed is an ENHANCEMENT, never a hard dependency.

## Architecture

Two independent layers, blended at the feed level:

- **Official layer (NEW):** YouTube Data API v3 (`https://www.googleapis.com/youtube/v3/`), authenticated with the OAuth `youtube.readonly` Bearer token. Flow: subscriptions → uploads → video metadata.
- **Local layer (EXISTS):** InnerTube search + watch history. No auth needed, always works. `fetchSearchBasedHomeFeed()` in `VideoRepositoryImpl`.
- **Blend + Room cache:** merged and cached, surfaced through `getHomeFeed()`.

## Key Technical Constraint

InnerTube's WEB client personalizes via SAPISID login **cookies**, NOT the OAuth Bearer token. That's why `FEwhat_to_watch` (and `FEsubscriptions`) return empty/non-personalized results in the current app. The official Data API v3 DOES use the OAuth token correctly, so the personalized subscriptions data must come from Data API v3, not InnerTube.

The Data API v3 algorithmic home feed (`activities.list?home=true`) was **deprecated in 2016**. Available official personalized data: `subscriptions.list`, `playlistItems.list` (channel uploads), `videos.list` (details/liked), `playlists`.

## Section 2 — Official Subscriptions Feed (new code)

Quota-optimized Data API v3 flow:
1. `subscriptions.list?part=snippet&mine=true&maxResults=50` (paginated via pageToken) → subscribed channel IDs + titles + thumbnails. Cache in Room `subscriptions` table.
2. **Skip `channels.list`** — derive each channel's uploads playlist deterministically by rewriting ID prefix `UC…` → `UU…`. Saves 1 quota batch.
3. `playlistItems.list?part=snippet&playlistId=UU…&maxResults=5` per channel → recent uploads.
4. `videos.list?part=snippet,statistics,contentDetails&id=<up to 50 comma-separated>` batched → view counts + durations for enrichment.

**Quota reality:** Data API v3 = **10,000 units/day per project, shared across ALL users**. Step 3 costs ~1 unit per subscribed channel per refresh. Personal/sideloaded use (1 user, 30–100 subs) is trivial. Large public base = bottleneck → mitigated by Room caching (refresh every few hours or on pull-to-refresh). **No API key needed** — OAuth Bearer authenticates Data API calls directly.

## Section 3 — Blending & Home Feed

`VideoRepositoryImpl.getHomeFeed()` new tiered chain:
1. **Authenticated + has subscriptions:** official subscriptions uploads (recent, date-sorted) as backbone, **interleaved item-by-item** with local watch-history/search recommendations. **Blend ratio: 40% subscriptions / 60% local (Discovery-heavy).** Mixed so it reads as one feed, not two blocks.
2. **Authenticated but official layer empty/fails (403/quota):** fall back to local search-based feed.
3. **Unauthenticated:** existing search-based + trending.

Cached to Room; pull-to-refresh forces re-fetch.

**Interleave algorithm:** for every 5 feed slots, ~2 come from subscriptions, ~3 from local recs; dedupe by videoId; preserve recency ordering within each source.

## Section 4 — Auth Changes (all-account + verification-ready)

- Keep minimal `youtube.readonly` scope (already minimal — good for verification).
- **Graceful OAuth handling:** in Testing mode, non-test accounts are blocked by Google; catch that failure and API 403 "app not verified", then fall back to local feed + show a subtle one-line banner: "Personalized subscriptions activate after app verification — showing recommendations from your activity."
- **In-app Privacy Policy link** in Settings (opens hosted URL).
- Room migration **v4 → v5** (`MIGRATION_4_5`) for a feed-cache table.

## Section 5 — Deliverables (verification package)

Files generated in repo under `docs/verification/`:
- `CHECKLIST.md` — step-by-step Google Cloud Console actions.
- `PRIVACY_POLICY.md` — ready-to-host privacy policy template.
- `DEMO_VIDEO_SCRIPT.md` — screen-by-screen recording script + written scope justification.

## Cost & Timeline (verification)

- **$0 to Google.** `youtube.readonly` is a SENSITIVE scope (not restricted) → no fee, no paid CASA security assessment.
- Timeline: brand verification ~2–3 business days + sensitive scope verification ~10 business days.

## Policy Risk (acknowledged)

App's core purpose = ad-free YouTube playback via InnerTube scraping. Google's YouTube API Services ToS prohibit interfering with ads / replicating YouTube. A human reviewer may REJECT verification on policy grounds regardless of technical readiness. The Hybrid design ensures the app is fully functional for all accounts WITHOUT verification; the official subscriptions feed is upside, not a dependency.

## New / Changed Components

**New files:**
- `data/api/YouTubeDataApiService.kt` — Retrofit interface for Data API v3 (subscriptions, playlistItems, videos).
- `data/api/dto/DataApiDtos.kt` — DTOs for Data API v3 responses.
- `data/repository/SubscriptionFeedRepository.kt` (or method set) — orchestrates subscriptions → uploads → videos with Room caching.

**Changed files:**
- `di/NetworkModule.kt` — add second Retrofit instance (baseUrl `https://www.googleapis.com/youtube/v3/`) + provide `YouTubeDataApiService`; reuse youtube OkHttpClient (already attaches Bearer).
- `data/repository/VideoRepositoryImpl.kt` — new tiered `getHomeFeed()` with 40/60 blend + interleave.
- `di/DatabaseModule.kt` + Room entities/DAO — `MIGRATION_4_5`, DB version 4→5, feed-cache table.
- `presentation/ui/screen/SettingsScreen.kt` — Privacy Policy link.
- `presentation/ui/screen/HomeScreen.kt` — degradation banner.

## Testing

- Unit tests for interleave/blend logic (deterministic given two input lists).
- Unit tests for `UC…`→`UU…` uploads-playlist derivation.
- Data API DTO parsing tests.
- Emulator: verify authenticated feed blends, unauthenticated falls back, 403 degrades gracefully.

## Out of Scope

- Cookie-based InnerTube personalization (gray-area, rejected).
- Real ML algorithmic "recommended for you" feed (requires cookie/gray-area path).
- Actual submission of verification (user does this; app just becomes ready).
