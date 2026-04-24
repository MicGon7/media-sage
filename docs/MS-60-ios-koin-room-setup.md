# MS-60: Wire Koin and Room for iOS

## What Was Done

After updating to macOS Tahoe and the latest Xcode, the iOS app required proper DI and database wiring to run on a physical device.

### Changes

- **`MainViewController.kt`** — Added `initKoin(serverBaseUrl: String)` to initialize Koin with `databaseModule`, `sharedModule`, and `appModule`
- **`iOSApp.swift`** — Added `init()` that reads `SERVER_BASE_URL` from Xcode scheme environment variables and calls `initKoin`
- **`DatabaseBuilder.ios.kt`** — Added `BundledSQLiteDriver` required by Room on iOS
- **`Config.xcconfig`** — Added `TEAM_ID` for code signing

## Key Decisions

### Xcode Scheme Environment Variables for Server URL

iOS doesn't have a `local.properties` equivalent. The idiomatic approach is Xcode scheme environment variables:

- Set in **Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables**
- Read at runtime via `ProcessInfo.processInfo.environment["SERVER_BASE_URL"]`
- The local scheme file is auto-gitignored by Xcode
- Mirrors Android's `local.properties` pattern without any extra plumbing

**For physical device:** set `SERVER_BASE_URL=http://<your-machine-ip>:8080`  
**For simulator:** set `SERVER_BASE_URL=http://localhost:8080` or omit (default fallback)

### BundledSQLiteDriver

Newer versions of Room on iOS require `.setDriver(BundledSQLiteDriver())` — without it the database fails to initialize on iOS.
