# MS-18: Project Scaffolding — KMP + Compose Multiplatform + Ktor Server

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-16

## What was built

Three-module Gradle project:

- **`shared/`** — Kotlin Multiplatform library targeting Android, iOS (arm64 + simulatorArm64). Contains Room database setup, Ktor Client, kotlinx-serialization, and Koin DI.
- **`composeApp/`** — Compose Multiplatform UI targeting Android and iOS. Depends on `:shared`. Includes Material3, Koin, and Lifecycle ViewModel.
- **`server/`** — JVM-only Ktor server using Netty. Configured with CORS, StatusPages, ContentNegotiation, and a health endpoint.

Also set up:
- `gradle/libs.versions.toml` — centralized version catalog for all dependencies
- `settings.gradle.kts` — typesafe project accessors enabled
- iOS app shell (`iosApp/`) with SwiftUI `ContentView` hosting the Compose view
- `.mcp.json` for Atlassian MCP server config

## Key decisions & why

- **Compose Multiplatform over platform-native UI**: Single UI codebase for Android and iOS. Michael already knows Compose, so learning curve is only the multiplatform parts, not a whole new UI framework.
- **Ktor for both client and server**: Same HTTP library on both sides reduces cognitive load. Ktor Client in `shared/` with platform engines (OkHttp for Android, Darwin for iOS). Ktor Server in `server/` with Netty.
- **Room 2.7.1 for persistence**: Room now supports KMP. Schemas stored in `shared/schemas/`.
- **Koin for DI**: KMP-compatible, lightweight, no code generation. Modules defined per feature.
- **Platform engines split**: `androidMain` uses `ktor-client-okhttp`, `iosMain` uses `ktor-client-darwin`. This is the standard KMP pattern — expect blocks aren't needed for Ktor, just source set dependencies.

## Concepts learned

- **KMP source sets**: `commonMain` for shared code, `androidMain`/`iosMain` for platform-specific implementations. `commonTest` for shared tests.
- **Version catalog** (`libs.versions.toml`): Centralized dependency management. Plugins and libraries referenced via `libs.plugins.*` and `libs.*` in build files.
- **Typesafe project accessors**: `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` in settings allows `projects.shared` instead of `project(":shared")`.
- **iOS framework**: The shared module compiles to a static iOS framework (`isStatic = true`) consumed by the SwiftUI app.

## Gotchas

- **KSP versioning changed**: KSP 2.3.6 uses a new scheme (just `2.3.6`, not `2.3.20-2.3.6`). This tripped up the initial setup.
- **Koin version compatibility**: Koin 4.0.4 had linking errors with Kotlin 2.3.20 — needed 4.2.1.
- **JVM target alignment**: All modules must agree on JVM 11 (`jvmTarget`, `sourceCompatibility`, `targetCompatibility`). Mismatches cause cryptic build errors.
