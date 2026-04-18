# MS-5: Set up Ktor routing structure & environment config

**Epic:** MS-1 (Server API Layer)
**Date completed:** 2026-04-17

## What was built

Transformed the server from a minimal hardcoded setup into a properly structured Ktor application:

- **`application.conf`** — HOCON config file replacing hardcoded `embeddedServer()`. Port, host, and API keys configurable via environment variables.
- **`Application.kt`** — Switched to `EngineMain` which reads `application.conf`. Module function orchestrates plugin installation and routing.
- **Plugin system** (each in its own file under `plugins/`):
  - `ContentNegotiation.kt` — JSON serialization via kotlinx-serialization
  - `CORS.kt` — Cross-origin access for local development
  - `CallLogging.kt` — Request/response logging via SLF4J/Logback
  - `StatusPages.kt` — Structured JSON error responses with `ErrorResponse` data class
- **Route modules** (under `routes/`):
  - `HealthRoutes.kt` — `/health` endpoint
  - `NewsRoutes.kt` — `/api/news` placeholder (MS-7)
  - `AnalysisRoutes.kt` — `/api/analysis` placeholder (MS-6)

## Key decisions & why

- **`EngineMain` over `embeddedServer()`**: `embeddedServer` hardcodes config in Kotlin. `EngineMain` reads `application.conf`, which supports environment variable overrides (`${?PORT}`) and keeps config separate from code. This is Ktor's recommended approach for production apps.
- **One file per plugin**: Each `configure*()` function lives in its own file. Keeps `Application.kt` clean as an orchestrator. When adding a new plugin, you create a file and add one line to `module()`.
- **`fun Application.configure*()` vs `fun Route.xxxRoutes()`**: Plugins extend `Application` (installed once at startup). Routes extend `Route` (called inside `routing {}` block). Different extension receivers for different purposes.
- **`ignoreUnknownKeys = true` in JSON config**: External APIs (Claude, News) may add fields we don't model. Without this, deserialization would crash on unknown fields.
- **`anyHost()` in CORS**: Wide open for local dev. CORS is a browser-only mechanism — mobile apps and CLI tools like curl ignore it entirely. Lock down before deployment.

## Concepts learned

- **HOCON format**: Human-Optimized Config Object Notation. Superset of JSON used by Ktor and other JVM tools. Supports env var substitution with `${?VAR}` (optional) or `${VAR}` (required).
- **Ktor plugin system**: `install(PluginName) { config }` adds middleware to the request/response pipeline. Order matters — StatusPages should catch exceptions from later plugins/routes.
- **Extension functions as architecture**: Kotlin extension functions (`fun Application.module()`) let you organize code across files while keeping it readable. The receiver type (`Application` vs `Route`) communicates intent.
- **CORS**: Browser security mechanism that restricts cross-origin HTTP requests. Not relevant for native mobile apps or server-to-server calls. Only matters when a browser's JavaScript makes requests to your server.
- **CallLogging**: Uses SLF4J under the hood, configured by `logback.xml`. Logs method, path, status code, and response time for every request.

## Gotchas

- **IDE reverted changes**: Android Studio reverted modified files when switching branches. Always verify `git status` before committing — don't assume your changes are still there.
- **Forgot to create branch**: Started working on `main` instead of a feature branch. Caught it before committing. Always create the branch immediately after transitioning the ticket to In Progress.
- **Forgot to move ticket to In Progress**: The workflow step of transitioning the Jira ticket was missed. Should be part of the automatic flow: create branch → transition ticket.
- **CallLogging not in version catalog**: The dependency `ktor-server-call-logging` wasn't in `libs.versions.toml` from scaffolding. Had to add it manually. When adding a new Ktor plugin, always check the version catalog first.
