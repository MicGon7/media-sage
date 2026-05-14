# MS-173: Fix TestFlight Upload — Duplicate Build Number

## Problem

TestFlight uploads failed with `ENTITY_ERROR.ATTRIBUTE.INVALID.DUPLICATE` on `cfBundleVersion`.

`Config.xcconfig` sets `CURRENT_PROJECT_VERSION=1`. Fastlane's `increment_build_number` writes to the xcodeproj file, but xcconfig overrides project-level build settings — so every build shipped with build number `1` regardless of what Fastlane set.

## Fix

Two changes:

**1. `testflight.yml`** — pass `github.run_number` as `BUILD_NUMBER` in the Deploy step env.

`github.run_number` is monotonically increasing per workflow, never repeats, and is traceable back to the specific CI run that produced the binary.

**2. `Fastfile`** — add `CURRENT_PROJECT_VERSION` to the `build_app` xcargs.

xcargs override xcconfig, so this ensures the correct build number reaches the binary regardless of what `Config.xcconfig` contains. Falls back to `Time.now.to_i.to_s` for local Fastlane runs where `BUILD_NUMBER` is not set.

## Key Distinction

- `CURRENT_PROJECT_VERSION` / `cfBundleVersion` — build number, must increment on every App Store Connect upload, never shown to users
- `MARKETING_VERSION` / `CFBundleShortVersionString` — user-facing version (`1.0`), unchanged by this fix

## xcconfig Override Precedence

xcargs > xcconfig > project settings. When a value appears in both xcconfig and xcargs, xcargs wins. This is the same pattern used for `SUPABASE_HOST` and `SUPABASE_ANON_KEY`.
