# Media Sage — Development Guide

## Project Overview

Media Sage is a Kotlin Multiplatform (KMP) app with a Ktor backend that integrates Claude API, News API, and Scripture API. It targets Android, iOS, and a JVM server.

## Architecture

Three-module Gradle project (`settings.gradle.kts`):

```
:composeApp   — Compose Multiplatform UI (Android + iOS)
:shared       — KMP library (networking, database, domain models)
:server       — Ktor backend (API orchestration, external service calls)
```

### Module Responsibilities

- **composeApp**: UI layer only. Depends on `:shared`. Uses Compose Material3, Koin for DI, and Lifecycle ViewModel.
- **shared**: Business logic, data layer, networking. Room for persistence, Ktor Client for HTTP, kotlinx-serialization for JSON. Platform engines: OkHttp (Android), Darwin (iOS).
- **server**: JVM-only Ktor server (Netty). Calls external APIs (Claude, News, Scripture). Uses Koin for DI, CORS, StatusPages, ContentNegotiation.

### Dependency Injection

Koin is used across all modules. Define modules per feature, not per layer.

## Tech Stack & Versions

Managed in `gradle/libs.versions.toml`:

| Technology | Version |
|---|---|
| Kotlin | 2.3.20 |
| AGP | 8.11.2 |
| KSP | 2.3.6 |
| Compose Multiplatform | 1.10.3 |
| Ktor | 3.1.3 |
| Room | 2.7.1 |
| Koin | 4.2.1 |
| kotlinx-serialization | 1.8.1 |
| kotlinx-coroutines | 1.10.2 |
| Android compileSdk | 36 |
| Android minSdk | 24 |

## Package Structure

```
com.mediasage/              — composeApp (UI)
com.mediasage.shared/       — shared module (eventually split by feature)
com.mediasage.server/       — server module
```

## Build & Run

```bash
# Run all tests
./gradlew allTests

# Run server
./gradlew :server:run

# Build Android
./gradlew :composeApp:assembleDebug

# Build iOS framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Conventions

### Git
- Branch naming: `feature/MS-{ticket}-short-description`, `fix/MS-{ticket}-short-description`
- Commit prefix: `MS-{ticket}: Description` (e.g., `MS-15: Add CLAUDE.md`)
- PRs follow `.github/pull_request_template.md`

### Code
- Kotlin code style: `official` (set in `gradle.properties`)
- JVM target: 11
- Use `kotlinx.serialization` for all JSON — no Gson/Moshi
- Use Ktor client in shared, Ktor server in server — never mix
- Room schemas stored in `shared/schemas/`

### Testing
- Common tests in `commonTest` source sets
- Server tests use `ktor-server-test-host` with `testApplication { }` DSL
- Shared module has `ktor-client-mock` with `MockEngine` for HTTP tests
- composeApp tests in `commonTest` — test platform-independent logic
- Koin test utilities available via `koin-test`
- Use `runTest` from `kotlinx-coroutines-test` for suspending test functions
- Every new feature must include tests — run `./gradlew allTests` before creating a PR
- Test file naming: `{ClassName}Test.kt` in the corresponding test source set

## Agent Guidelines

### Picking up tickets
- Check Jira (project key: MS) for ticket details and acceptance criteria
- Tickets marked "Agent Safe: Yes" can be completed autonomously
- Tickets marked "Agent Safe: Partial" — implement what you can, flag decisions for human review

### Before submitting work
- Run `./gradlew allTests` and ensure all pass
- No API keys or secrets in code — use environment variables or local.properties
- Update this file if introducing a new architectural pattern

## Project Tracking

- Jira project: Media Sage (key: MS) at media-sage.atlassian.net
- Kanban board — no sprints, track time via In Progress → Done transitions
- Epics serve as objectives: MS-1 (Server API Layer), MS-2 (Shared Data Layer), MS-3 (App UI), MS-4 (Infrastructure)
