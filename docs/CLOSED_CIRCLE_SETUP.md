# FreedomPlay — Closed-Circle Setup Guide

## Quick Start (for you, the distributor)

### 1. Google Cloud Console Setup (one-time, ~10 minutes)

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (or create one)
3. Navigate to **APIs & Services > OAuth consent screen**
4. Keep publishing status as **Testing** (do NOT switch to Production)
5. Add test users: up to 100 Gmail addresses
6. Enable **YouTube Data API v3** in the API Library

### 2. Distribute the App

1. Build release APK: `.\gradlew.bat assembleRelease`
2. Share the APK via WhatsApp, Telegram, or any file-sharing method
3. Each user installs by enabling "Install from unknown sources"

### 3. User Sign-In Flow

Each user opens the app → taps Sign In → selects their Google account.
The app requests minimal scopes (email, profile, YouTube read-only).

**Important:** Test-user refresh tokens expire every 7 days.
When a user gets logged out, they simply sign in again.

### 4. Personalized Feed (Cookie Method)

For a YouTube-like personalized feed, users can optionally connect their YouTube session cookies:

1. Open YouTube in Chrome on desktop
2. Open DevTools (F12) → Application → Cookies → youtube.com
3. Copy the full cookie string and the SAPISID value
4. In FreedomPlay → Settings → Connect YouTube Account
5. Paste both values

This is optional — the app works fully without it (uses search/watch-history recommendations).

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Sign-in fails | Revoke access at myaccount.google.com, try again |
| Feed shows generic content | Connect cookies for personalized feed |
| App crashes | Clear app data, sign in again |
| Token expired (7-day) | Simply sign in again — this is normal for test apps |

## Security Notes

- Cookies are stored locally with XOR obfuscation (not encrypted)
- Never share your cookie string with anyone
- FreedomPlay does not send cookies to any server except YouTube