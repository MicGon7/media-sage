# MS-170: Fix iOS Build Config — Supabase Credentials and Export Compliance

## What changed

- `MainViewController.kt` — `initKoin()` now accepts `supabaseUrl` and `supabaseAnonKey` parameters instead of hardcoding empty strings
- `iOSApp.swift` — reads both values from `Bundle.main.infoDictionary` and passes them to `initKoin()`
- `iosApp/Configuration/Config.xcconfig` — adds `SUPABASE_HOST` and `SUPABASE_ANON_KEY` with empty defaults (filled in locally, never committed)
- `iosApp/iosApp/Info.plist` — exposes `SUPABASE_URL` (`https://$(SUPABASE_HOST)`) and `SUPABASE_ANON_KEY` from xcconfig; adds `ITSAppUsesNonExemptEncryption = false`
- `fastlane/Fastfile` — strips protocol from `SUPABASE_URL` env var, passes `SUPABASE_HOST` and `SUPABASE_ANON_KEY` as xcargs overrides

## Why Supabase was broken on iOS

`MainViewController.kt` hardcoded empty strings for both Supabase values. `SharedModule.kt` only registers `SupabaseClient` when both values are non-blank — so auth was completely non-functional in every iOS build. Android was unaffected because it receives the values via `BuildConfig`.

## The idiomatic pattern

The correct KMP approach is to pass values from the Swift entry point into Kotlin — not to read platform APIs from Kotlin. `iOSApp.swift` owns the app lifecycle and has access to `Bundle.main.infoDictionary`; it passes the values into `initKoin()`. Kotlin stays clean.

## xcconfig `//` comment gotcha

Storing a full URL (`https://...`) in xcconfig silently strips everything from `//` onward — xcconfig treats `//` as a comment delimiter. The symptom was a DNS failure: the URL being used was literally `https:` instead of the full domain.

**Fix:** store only the host in xcconfig (`SUPABASE_HOST=...`), reconstruct the full URL in `Info.plist` (`https://$(SUPABASE_HOST)`). xcargs in Fastlane also strips the protocol before passing to Xcode.

## Local credential safety

`Config.xcconfig` is tracked in git with empty defaults. Real credentials are filled in locally and protected from accidental commits via:

```bash
git update-index --assume-unchanged iosApp/Configuration/Config.xcconfig
```

Any new contributor needs to run this command and fill in their own values locally.

## Local-first debugging saved significant CI time

Three issues were caught locally before any CI run:
1. Stale KMP framework cache breaking release (fixed with `Product → Clean Build Folder`)
2. xcconfig `//` comment stripping the Supabase URL
3. Auth failure confirming the full fix worked end-to-end

Each would have been a 25+ minute TestFlight CI burn to discover.

## Files changed

- `composeApp/src/iosMain/kotlin/com/mediasage/MainViewController.kt`
- `iosApp/iosApp/iOSApp.swift`
- `iosApp/Configuration/Config.xcconfig`
- `iosApp/iosApp/Info.plist`
- `fastlane/Fastfile`
