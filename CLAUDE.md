# Media Sage — Development Guide

## Project Overview

Media Sage (public name: **The Media Sage**) is a Kotlin Multiplatform (KMP) app with a Ktor backend that matches news headlines with encouraging quotes from Christian theologians, mystics, and biblical figures using Claude AI. It targets Android, iOS, and a JVM server.

## Architecture

Three-module Gradle project (`settings.gradle.kts`):

```
:composeApp   — Compose Multiplatform UI (Android + iOS)
:shared       — KMP library (networking, database, domain models)
:server       — Ktor backend (API orchestration, external service calls)
```

### Module Responsibilities

- **composeApp**: UI layer only. Depends on `:shared`. Uses Compose Material3, Koin for DI, Lifecycle ViewModel, and Nav3 for navigation.
- **shared**: Business logic, data layer, networking. Room for persistence, Ktor Client for HTTP, kotlinx-serialization for JSON. Platform engines: OkHttp (Android), Darwin (iOS).
- **server**: JVM-only Ktor server (Netty). Calls external APIs (Claude, News, Scripture). Uses Koin for DI, CORS, StatusPages, ContentNegotiation, CallLogging.

### Data Flow

Room is the single source of truth. The UI always reads from Room via Flow. Network calls update Room.

```
Server JSON → Client DTO → Room Entity → Domain Model → UI
```

- **DTOs** (`data/remote/`) — server response shapes, serialization only
- **Entities** (`data/local/entity/`) — Room database schema
- **Domain Models** (`domain/model/`) — clean types for UI (enums, lists)
- **Repositories** (`data/repository/`) — bridge all three layers

### UI Architecture (MVI Contract Pattern)

Each feature has 3 files under `composeApp/src/commonMain/kotlin/com/mediasage/feature/{name}/`:

| File | Purpose |
|------|---------|
| `{Name}Contract.kt` | UiState (sealed interface) + Intent (sealed interface) + SideEffect (sealed interface) |
| `{Name}ViewModel.kt` | Processes intents, emits state via StateFlow, side effects via Channel |
| `{Name}Screen.kt` | Stateless composable — receives state, onIntent, and navigation lambdas |

Key conventions:
- **Sealed interfaces for UiState**: Loading, Success, Error — mutually exclusive, no invalid combinations
- **Channels for side effects**: One-off events (navigation, snackbar) via `Channel` → `receiveAsFlow()`
- **`state` not `uiState`**: The type name already says UiState
- **Screens are stateless**: Receive state + callbacks, no ViewModel dependency. Previewable and testable.
- **No base ViewModel class**: Convention over abstraction

### Navigation (Nav3)

- **`navigation/Routes.kt`** — Sealed interface `Route` with type-safe destinations
- **`navigation/TopLevelDestination.kt`** — Enum of bottom nav tabs with route, label, icon
- **`navigation/MediaSageAppState.kt`** — Centralizes navigation: `isTopLevel`, `titleRes`, navigate methods
- **`navigation/MediaSageScaffold.kt`** — Top-level Scaffold with AppState-driven top bar and bottom bar

### Dependency Injection

Koin is used across all modules. Define modules per feature, not per layer.
- **Server**: `serverModule(claudeApiKey, newsApiKey, scriptureApiKey)` — HttpClient, API services
- **Shared**: `sharedModule(serverBaseUrl)` — HttpClient, MediaSageApi, repositories

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
| Nav3 UI (JetBrains) | 1.0.0-alpha05 |
| Detekt | 1.23.8 |
| Kover | 0.9.8 |
| kotlinx-serialization | 1.8.1 |
| kotlinx-coroutines | 1.10.2 |
| Android compileSdk | 36 |
| Android minSdk | 24 |

## Package Structure

```
composeApp/src/commonMain/kotlin/com/mediasage/
├── App.kt
├── theme/               — Color, Type, Theme (MediaSageTheme)
├── navigation/          — Routes, AppState, Scaffold, TopLevelDestination
└── feature/
    ├── home/            — HomeContract, HomeViewModel, HomeScreen
    ├── match/           — MatchContract, MatchViewModel, MatchScreen
    └── figures/         — FiguresContract, FiguresViewModel, FiguresScreen (UI label: "Voices")

shared/src/commonMain/kotlin/com/mediasage/
├── di/                  — Koin modules
├── domain/
│   ├── model/           — Figure, Quote, Headline, Match
│   └── repository/      — Repository interfaces
└── data/
    ├── local/
    │   ├── entity/      — Room entities
    │   ├── dao/         — Room DAOs
    │   └── db/          — Database, converters, platform builders
    ├── remote/          — MediaSageApi, DTOs, HttpClientFactory
    ├── repository/      — Repository implementations
    └── mapper/          — Entity ↔ Domain mappers

server/src/main/kotlin/com/mediasage/server/
├── Application.kt       — Entry point, Koin setup
├── plugins/             — ContentNegotiation, CORS, CallLogging, StatusPages
├── routes/              — Health, News, Analysis, Scripture
├── service/             — ClaudeApiService, NewsApiService, ScriptureApiService
└── di/                  — ServerModule
```

## Build & Run

```bash
# Run all tests
./gradlew allTests

# Run Detekt
./gradlew detekt

# Run server (requires API keys in ~/.zshrc)
source ~/.zshrc && ./gradlew :server:run

# Build Android
./gradlew :composeApp:assembleDebug

# Build iOS framework
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Coverage report
./gradlew koverHtmlReport
```

## Conventions

### Git
- Branch naming: `feature/MS-{ticket}-short-description`, `fix/MS-{ticket}-short-description`
- Commit prefix: `MS-{ticket}: Description`
- PRs follow `.github/pull_request_template.md`
- Trunk-based development — short-lived branches, merge to main

### Code
- Kotlin code style: `official` (set in `gradle.properties`)
- JVM target: 11
- Use `kotlinx.serialization` for all JSON — no Gson/Moshi
- Use Ktor client in shared, Ktor server in server — never mix
- Room schemas stored in `shared/schemas/`
- `@SerialName` annotations on their own line above the property
- String resources in `composeResources/values/strings.xml` — no hardcoded strings in UI
- API keys stored as env vars in `~/.zshrc`, read via `application.conf`

### Testing
- Common tests in `commonTest` source sets
- Server tests use `ktor-server-test-host` with `testApplication { }` DSL
- Shared module has `ktor-client-mock` with `MockEngine` for HTTP tests
- composeApp tests in `commonTest` — test platform-independent logic
- Use `runTest` from `kotlinx-coroutines-test` for suspending test functions
- Every new feature must include tests — run `./gradlew allTests` before creating a PR
- Smoke test external API changes with real APIs before creating a PR

### Quality Gates
- **Detekt**: Runs in CI before build. `./gradlew detekt` must pass.
- **Kover**: Coverage reports generated in CI, uploaded as artifacts. Target: 70% line coverage (Phase 2).

## Agent Guidelines

### Workflow
1. Query Jira for assigned tickets: `project = MS AND labels in (assisted, autonomous) AND status = "To Do"`
2. Create Jira ticket if new work (with acceptance criteria)
3. Transition ticket to In Progress
4. Create feature branch (`feature/MS-{ticket}-description`)
5. Do the work
6. Update Jira AC checkboxes
7. Update `docs/` with ticket learning doc
8. Update this file (CLAUDE.md) if introducing new patterns
9. Commit everything, push, create PR
10. Transition ticket to In Review
11. Reply to any PR review comments with `🤖 **Agent:**` prefix

### Before submitting work
- Run `./gradlew allTests` and ensure all pass
- Run `./gradlew detekt` and ensure no violations
- Smoke test any external API changes with real APIs
- No API keys or secrets in code — use environment variables
- Never push directly to main — always create a PR
- Never merge a PR — human reviews and merges

### Autonomous Mode

An **autonomous agent** runs the full workflow — Jira, branch, code, tests, docs, commit, PR — without human interaction. The human's only touchpoint is the GitHub PR review.

**Jira labels that control agent behavior:**

- No label — human work with no AI involvement.
- **`assisted`** — Human and AI work together. Human stays in the loop, approves decisions, and learns the patterns. AI acts as a pair programmer. This is the default for all AI-intended tickets and the expected minimum standard for the team.
- **`autonomous`** — Explicit upgrade from `assisted`. AI works alone end-to-end, human only reviews the PR. Only appropriate for patterns that are already proven and well-understood.

`assisted` is always the starting point. Promote to `autonomous` only after the pattern has been built and validated in `assisted` mode. When in doubt, stay `assisted`.

**Invocation:**
```bash
# Assisted — human approves tool calls (interactive Claude Code session)
claude

# Autonomous — bootstrap command (always the same, ticket is the prompt)
claude -p "Check Jira (cloudId: media-sage.atlassian.net) for the next ticket labeled 'autonomous' in project MS with status 'To Do'. Read the ticket description and acceptance criteria for your task. Follow the Agent Guidelines in CLAUDE.md and execute the full workflow." \
  --dangerously-skip-permissions
```

The bootstrap command never changes — the **ticket is the prompt**. Every autonomous ticket must include a clear task description and explicit acceptance criteria so the agent has everything it needs without human input.

**Autonomous ticket requirements:**
- Title: concise task description (agent uses this as the task summary)
- Description: what needs to change and why
- Acceptance criteria: explicit checkboxes the agent checks off as it works
- Label: `autonomous`
- No ambiguous requirements — if it needs clarification, use `assisted` instead

**Automation levels:**

- **Level 1 — Assisted**: Human writes the prompt and approves every tool call. Run via an interactive `claude` session.
- **Level 2 — Autonomous (manual trigger)**: Human describes the intent to the assisted agent, which creates the Jira ticket with AC and the `autonomous` label. Human fires the bootstrap command once, then only reviews the PR. Run via `claude -p "..." --dangerously-skip-permissions`.
- **Level 3 — Autonomous (self-triggering)**: Human describes the intent to the assisted agent, which creates the Jira ticket. Human walks away — a cron job or Jira webhook fires the bootstrap automatically. Human only reviews the PR.

_This project is at Level 2. Level 3 requires a cron job or webhook that polls Jira and fires the bootstrap when an `autonomous` ticket enters To Do._

**Autonomous vs Assisted:**

- **Human touchpoints**: Autonomous = PR review only. Assisted = every tool call.
- **Speed**: Autonomous = minutes. Assisted = hours.
- **Best for**: Autonomous = well-defined tasks where all patterns already exist. Assisted = exploratory work, new architecture, anything ambiguous.
- **Risk**: Autonomous = mistakes reach the PR before any human sees them. Assisted = human can course-correct mid-run.

**When to use autonomous mode:**
- Task is well-scoped with a clear acceptance criterion
- All patterns already exist in the codebase (no novel architecture decisions)
- The diff will be small enough for a reviewer to catch any mistakes

**When NOT to use autonomous mode:**
- First implementation of a new pattern (e.g., new data layer, new nav pattern)
- Tasks requiring external smoke tests (live API calls, device testing)
- Anything that touches database migrations, security, or auth
- Ambiguous tasks where requirements need clarification

**Pros:**
- Executes the full workflow in minutes with no context switching for the developer
- Enforces consistency — never skips detekt, tests, docs, or Jira updates
- Scales horizontally — multiple agents can work different tickets in parallel

**Cons:**
- Mistakes run all the way to a PR before anyone sees them — good PR review hygiene is essential
- Requires pre-approved tool permissions (trust boundary is the whole session)
- Context window limits: very large tasks may need to be broken into smaller tickets
- No mid-run judgment — if the task turns out to be more complex, the agent may produce incomplete work rather than stopping to ask

## Project Tracking

- Jira project: Media Sage (key: MS) at media-sage.atlassian.net
- Kanban board — no sprints, track time via In Progress → In Review → Done transitions
- Epics: MS-1 (Server API Layer), MS-2 (Shared Data Layer), MS-3 (App UI), MS-4 (Infrastructure)
- Auto-transition: Jira tickets move to Done on PR merge via GitHub Actions
- Board settings: found under the three-dot menu next to the project in the recents sidebar → Board
