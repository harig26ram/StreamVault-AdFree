# Personalized Feed (Cookie-ML) + Themeable Magazine UI + Closed-Circle Login — Design Spec

**Date:** 2026-07-12
**Status:** Approved (design), pending implementation plan
**Author:** FreedomPlay / StreamVault-AdFree

## Goal

1. Give the user a **personalized home feed driven by their Google/YouTube account**, like the real YouTube feed — implemented as a **Cookie-ML feed**: real personalized InnerTube browse (`FEwhat_to_watch`) using the user's YouTube session cookies, blended with the existing local watch-history/search recommendations as a fallback base.
2. Ship a **themeable magazine-grid UI**: home feed uses a 2-column magazine grid (1 featured wide card + compact detail rows), and the app exposes an **in-app theme selector** with all accent themes selectable at runtime.
3. Make **premium controls collapsible** in the player (Download/EQ behind expandable panels, EQ hidden by default) — never always-open clutter.
4. Enable **closed-circle distribution** — shared privately with a small group (well under 100 people), NOT published to the Play Store. Sign-in via Google OAuth **test users** so no verification is needed.

## Core Principle: Graceful Degradation

The **local layer always produces a feed** (no auth needed). The **cookie-ML layer enhances** it when valid YouTube session cookies are present. If cookies are missing, expired, or the personalized request fails → the app silently falls back to local-only. Nothing ever hard-breaks. The personalized feed is an ENHANCEMENT, never a hard dependency.

## Architecture

Two independent layers, blended at the feed level:

- **Cookie-ML layer (NEW — top priority):** Real InnerTube browse using the user's YouTube **session cookies** (SAPISID/__Secure- variants + LOGIN_INFO). Sends `FEwhat_to_watch` browse with a correct `SAPISIDHASH` Authorization header + `Cookie` header + `X-Goog-Visitor-Data`. This is the ONLY way InnerTube returns a genuinely personalized feed (the WEB client ignores the OAuth Bearer token). Returns the real "recommended for you" + subscriptions shelf.
- **Local layer (EXISTS):** InnerTube search + watch history, no auth. Always works. `fetchSearchBasedHomeFeed()` in `VideoRepositoryImpl`. This is the fallback base.
- **Blend + Room cache:** merged and cached, surfaced through `getHomeFeed()`.

## Key Technical Constraint

InnerTube personalizes via **SAPISID login cookies**, NOT the OAuth Bearer token. That's why the current app's `FEwhat_to_watch` returns empty — it sends the OAuth Bearer but no session cookies. To get the real feed we must send the user's YouTube session cookies.

**Tradeoffs of the cookie approach (honest):**
- **Fragility:** YouTube increasingly requires a `PoToken` + BotGuard (WebViewService) proof on browse calls. Cookie-only requests may hit `HTTP 400/429` or empty shelves until PoToken is solved. Mitigation: robust fallback to local layer so the app never breaks.
- **Security:** Cookies = a full Google session (wider blast radius than a scoped OAuth token). Mitigation (see below): store encrypted in `DataStore` (not plaintext `SharedPreferences`), clearly label the "Connect account" affordance, allow disconnect/revoke anytime, never log cookie values.

## Section 2 — Cookie-ML Feed (new code)

**Cookie acquisition (closed-circle friendly, explicit + safe):**
- Settings → "Connect YouTube account" opens a cookie-paste dialog (user copies the `Cookie:` header / cookie string from their signed-in browser, e.g. via a devtools copy). No brittle WebView scraping.
- Store encrypted in `DataStore` (`data/store/CookieStore.kt`), NOT plaintext.
- `disconnect()` clears cookies immediately.

**Request flow (`CookieFeedRepository`):**
1. If cookies present → build InnerTube browse request for `browseId=FEwhat_to_watch` with headers: `Authorization: SAPISIDHASH <time>_<hash>`, `Cookie: <stored>`, `X-Goog-Visitor-Data`, existing User-Agent.
2. Parse personalized shelf (`parseBrowseResponse` extended/patched to extract `twoColumnBrowseResults` sections).
3. On empty / 400 / 429 / auth error → fall through to local layer.
4. Cache personalized results in a Room feed-cache table; refresh on pull-to-refresh or every few hours.

**Feed-source priority in `getHomeFeed()`:**
1. Cookie-ML personalized shelf (if cookies valid + non-empty) — blended with local recs so it reads as one feed.
2. Local search-based + watch-history feed (always works).
3. Trending fallback.

## Section 3 — Blending & Home Feed UI

- Home feed = **magazine grid**: 1 featured wide card (top) + 2-column grid of cards below. **Detail row below each thumbnail is compact** (title 1 line + channel name, smaller font, tighter spacing) per user request.
- Premium controls surfaced per-card as lightweight inline icons (download / save / PiP / EQ / queue) — mirror existing `VideoCard` `onSaveToWatchLater` + new handlers.
- Player premium controls (Download/EQ) are **collapsible panels**: Download open by default, **EQ hidden by default**, expandable (matches sample8 pattern).
- Pull-to-refresh (already implemented) forces re-fetch of the cookie-ML layer + local layer.

## Section 4 — Theming (in-app selector)

All accent themes selectable at runtime (default **Hot Pink #FF4081**):
- Hot Pink `#FF4081` (default), Digital Waves `#4FC3F7`, Eco Frequency `#69F0AE`, Neon Purple `#B388FF`, Amber Horizon `#FFB74D`, Crimson `#FF5252`.

Implementation: `ThemeManager` (DataStore-backed) + `Theme.kt` definitions exposing an `accent` color used by `MaterialTheme.colorScheme.primary`/related pink surfaces. Settings screen gets a theme selector (swatch row / list). Selection applies instantly app-wide.

## Section 5 — Auth Changes (closed-circle, robust sign-in)

- Keep minimal `youtube.readonly` OAuth scope for **identity** (profile/email) + test-user distribution. The personalized *feed* comes from cookies (Section 2), not OAuth.
- **OAuth stays in "Testing" mode.** Each member's Gmail added as an OAuth **test user** (up to 100) → full sign-in, no verification needed.
- **7-day refresh-token expiry:** on refresh failure → `googleSignInClient.silentSignIn()`; if that fails, gentle non-blocking re-sign-in prompt while local feed keeps working. Never wipe session.
- Cookie session expiry handled separately: if cookie-ML requests fail repeatedly, drop to local feed + subtle banner "Showing recommendations from your activity — reconnect account for your feed."

## Section 6 — Deliverables (closed-circle setup guide)

`docs/CLOSED_CIRCLE_SETUP.md` covering: (1) enable YouTube Data API v3 in Cloud project (for OAuth identity), (2) add each member's Gmail as OAuth test user, (3) **how members connect their YouTube account (paste cookies) for the personalized feed**, security note on cookie storage, (4) build + distribute APK privately, (5) 7-day re-sign-in + cookie expiry behavior.

## Cost & Timeline

- **$0, no OAuth verification.** Testing-mode test users need no fee, no CASA, no submission.
- Setup is minutes: enable API, add test-user emails, share APK.

## Policy Risk (lowered for this use case)

App stays in Testing mode for a private group → no human policy review of the OAuth project. The cookie-based feed is gray-area (like the existing InnerTube scraping) but adds no new policy class. Cookie storage is encrypted + user-explicit + revocable to limit blast radius.

## New / Changed Components

**New files:**
- `data/store/CookieStore.kt` — encrypted DataStore for YouTube session cookies.
- `data/repository/CookieFeedRepository.kt` — personalized browse (FEwhat_to_watch) with cookie headers + fallback.
- `theme/ThemeManager.kt` — DataStore-backed accent theme selection.
- `theme/Theme.kt` — accent theme definitions (6 themes).
- `presentation/ui/components/MagazineFeed.kt` — magazine-grid composable (featured + 2-col + compact detail).
- `presentation/ui/components/CollapsiblePanel.kt` — reusable expand/collapse panel for player Download/EQ.
- `docs/CLOSED_CIRCLE_SETUP.md` — setup guide.

**Changed files:**
- `di/NetworkModule.kt` — attach cookie headers on InnerTube browse when available; provide `CookieFeedRepository`.
- `data/repository/VideoRepositoryImpl.kt` — `getHomeFeed()` priority: cookie-ML → local → trending; magazine-grid data shape unchanged.
- `presentation/ui/screen/HomeScreen.kt` — use `MagazineFeed`; degradation banner.
- `presentation/ui/screen/PlayerScreen.kt` — wrap Download/EQ in collapsible panels (EQ hidden default).
- `presentation/ui/screen/SettingsScreen.kt` — theme selector + "Connect YouTube account" cookie dialog + disconnect.
- `auth/AuthManager.kt` — robust 7-day-expiry handling (unchanged from prior design).
- `di/DatabaseModule.kt` / entities / DAO — feed-cache table if needed (Room v4→v5 `MIGRATION_4_5`).

## Testing

- Unit: `parseBrowseResponse` extraction from a saved personalized `FEwhat_to_watch` response.
- Unit: blend/interleave determinism; cookie-store encrypt/decrypt round-trip.
- Unit: theme definition mapping (accent → color scheme).
- Emulator: with cookies → personalized feed; without → local fallback; PlayerScreen Download/EQ collapse behavior; theme switch applies app-wide.

## Out of Scope

- OAuth verification / Play Store publishing (not needed — closed-circle).
- Fully automatic WebView cookie scraping (use explicit paste for robustness).
- Solving PoToken/BotGuard end-to-end (deferred; fallback covers it).
