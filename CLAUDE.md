# Media Sage — Development Guide

## Project Overview

Media Sage (public name: **The Media Sage**) is a Kotlin Multiplatform (KMP) app with a Ktor backend that matches news headlines with encouraging quotes from Christian theologians, mystics, and biblical figures using Claude AI. It targets Android, iOS, and a JVM server.

## Architecture

Five-module Gradle project (`settings.gradle.kts`):

```
:composeApp   — Compose Multiplatform UI (Android + iOS)
:shared       — KMP library (networking, database, domain models)
:server       — Ktor app API, deployed to Railway (port 8080)
:agent        — Ktor orchestration server, deployed as Docker container on Railway (port 8081)
:scripts      — One-off batch jobs, run manually (no server, no Koin wiring)
```

### Module Responsibilities

- **composeApp**: UI layer only. Depends on `:shared`. Uses Compose Material3, Koin for DI, Lifecycle ViewModel, and Nav3 for navigation.
- **shared**: Business logic, data layer, networking. Room for persistence, Ktor Client for HTTP, kotlinx-serialization for JSON. Platform engines: OkHttp (Android), Darwin (iOS).
- **server**: JVM-only Ktor server (Netty). Calls external APIs (Claude, News, Scripture). Uses Koin for DI, CORS, StatusPages, ContentNegotiation, CallLogging. Deployed to Railway.
- **agent**: JVM-only Ktor server (Netty, port 8081). Receives Jira and GitHub webhooks, spawns Claude Code worker processes. No database deps. Deployed as a Docker container on Railway.
- **scripts**: JVM-only standalone scripts. No Ktor server, no Koin. Uses Exposed + SQLite/Postgres for DB access. Run manually via Gradle tasks (e.g., `generateImages`).

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
- **Screen parameters — hard rule**: A screen composable accepts exactly three kinds of parameters: `state`, `onIntent`, and navigation lambdas (`onNavigateTo*`). Nothing else. No booleans, no config, no extras.
- **Ambient config via CompositionLocal**: Values that are needed deep in the tree but are not dynamic state (e.g., `isDebugBuild`) use `CompositionLocal`. Define a `compositionLocalOf { default }` in `commonMain`, provide it once in `App`, read it with `.current` inside the composable. See `LocalIsDebugBuild.kt`.
- **UiState holds UI state, not build config**: Static build-time constants (e.g., debug flags) do not belong in `UiState` or ViewModel. They are ambient environment values, not runtime state.
- **`expect/actual` is for platform API differences only**: Never use `expect/actual` for build config constants (e.g., `isDebugBuild`). Doing so creates duplicate class entries in the Android dex and causes `NoSuchMethodError` crashes when the build cache serves a stale artifact. Pass build config as a `Boolean` parameter from each platform entry point (`MainActivity`, `MainViewController`) down to `App`.

### Navigation (Nav3)

- **`navigation/Routes.kt`** — Sealed interface `Route` with type-safe destinations
- **`navigation/TopLevelDestination.kt`** — Enum of bottom nav tabs with route, label, icon
- **`navigation/MediaSageAppState.kt`** — Centralizes navigation: `isTopLevel`, `titleRes`, navigate methods
- **`navigation/MediaSageScaffold.kt`** — Top-level Scaffold with AppState-driven top bar and bottom bar

### Dependency Injection

Koin is used across all modules. Define modules per feature, not per layer.
- **Server**: `serverModule(claudeApiKey, newsApiKey, scriptureApiKey, baseUrl)` — HttpClient, API services
- **Agent**: `agentModule(config, scope)` — HttpClient, AgentLaunchService, JiraApiService
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
├── routes/              — Health, News, Encourage, Scripture, Figures, DailyReflection
├── service/             — ClaudeApiService, NewsApiService, ScriptureApiService
└── di/                  — ServerModule

agent/src/main/kotlin/com/mediasage/agent/
├── Application.kt       — Entry point, Koin setup (port 8081)
├── di/                  — AgentConfig, AgentModule
├── plugins/             — ContentNegotiation, CallLogging, StatusPages
├── routes/              — JiraWebhookRoutes, GitHubWebhookRoutes
├── service/             — AgentLaunchService, JiraApiService (JiraLabelChecker)
└── tools/               — ToolDefinitions (Anthropic orchestrator-worker pattern)

scripts/src/main/kotlin/com/mediasage/scripts/
├── GenerateFigureImages.kt  — Portrait batch generation entry point
└── service/
    ├── ImageGenerationService.kt  — OpenAI gpt-image-2 client
    └── ScriptsDatabase.kt         — Minimal Exposed DB access (figures table)
```

## Build & Run

```bash
# Run all tests
./gradlew allTests

# Run Detekt
./gradlew detekt

# Run app API server (port 8080 — requires API keys in ~/.zshrc)
source ~/.zshrc && ./gradlew :server:run

# Run agent orchestration server locally (port 8081 — requires AGENT_REPO_PATH, Jira, GitHub env vars)
source ~/.zshrc && ./gradlew :agent:run

# Build agent container image locally
docker build -f agent/Dockerfile -t media-sage-agent .

# Run agent container locally (replace values as needed)
docker run -p 8081:8081 \
  -e ANTHROPIC_API_KEY=... \
  -e AGENT_REPO_PATH=/home/agent/media-sage \
  -e GITHUB_BOT_TOKEN=... \
  -e GITHUB_BOT_LOGIN=media-sage-bot \
  -e GITHUB_BOT_EMAIL=... \
  -e GITHUB_WEBHOOK_SECRET=... \
  -e JIRA_EMAIL=... \
  -e JIRA_API_TOKEN=... \
  media-sage-agent

# Generate figure portraits (batch script — requires DB_PATH, OPENAI_API_KEY)
./gradlew :scripts:generateImages -PscriptArgs="--batch-size=5 --quality=low --dry-run"

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
- Run `./gradlew allTests` and ensure all pass. **Exception: when running inside the container (Linux, no Android/iOS SDK), run `./gradlew :agent:test :server:test :shared:jvmTest` instead — Android and iOS targets require their respective SDKs and cannot run on Linux.**
- Run `./gradlew detekt` and ensure no violations
- Smoke test any external API changes with real APIs before writing the learning doc or opening a PR — docs should describe verified behaviour, not assumed behaviour
- No API keys or secrets in code — use environment variables
- Never push directly to main — always create a PR
- Never merge a PR — human reviews and merges

### After a PR is merged
Update the Agentic Development Impact page in Confluence (media-sage.atlassian.net, page ID: `163844`) at natural milestones — not after every individual PR. Batch small or related tickets (e.g. a hotfix paired with a feature) into a single update. A good trigger is when a feature is fully working end-to-end in production.

For each batch, add:
1. A new row in the **Ticket Log** table covering: ticket key, description, mode (`assisted` or `autonomous`), platform breakdown of engineer hours, total traditional hours, traditional cost, actual wall-clock hours, and key notes
2. Updated **Running Impact Totals** (cumulative hours and cost)

**Estimation rules for the Ticket Log:**
- Treat the work as if a digital agency built it in native (separate iOS, Android, and backend engineers — even though KMP shares code)
- Assign hours per platform only if that platform was actually touched by the ticket
- Add **25% overhead** on top of engineer hours for PM coordination, QA, code review cycles, staging deployment, and client demos
- **Rate: $130/h** blended agency rate (Staff $160/h, mid-level iOS/Android $130/h, Backend $140/h, QA $110/h)
- Example: a ticket touching Backend (8h) + iOS (6h) + Android (6h) = 20 engineer-hours + 5h overhead = 25h total = $3,250

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
- **Level 3 — Autonomous (self-triggering)**: Human describes the intent to the assisted agent, which creates the Jira ticket. Human walks away — a Jira webhook fires the bootstrap automatically when the ticket enters To Do. Human only reviews the PR.
- **Level 4 — Autonomous (self-responding to PR review)**: Human leaves a review comment on a PR for an `autonomous`-labeled ticket. A GitHub webhook fires the agent, which pushes a fix commit or replies with `🤖 **Agent:**`. Human's only touchpoint remains the PR review.

_This project is at Level 4. Both the Jira webhook (`POST /webhook/jira`) and the GitHub webhook (`POST /webhook/github`) are live in the `:agent` module, deployed as a Docker container on Railway._

**Level 3 & 4 setup (container — production):**

The `:agent` server runs as a Docker container on Railway. It clones the repo at startup using the bot account token, then starts the Ktor server.

Railway `:agent` service environment variables:

| Variable | Value |
|---|---|
| `AGENT_REPO_PATH` | `/home/agent/media-sage` |
| `ANTHROPIC_API_KEY` | Anthropic account API key |
| `GITHUB_BOT_TOKEN` | PAT for `media-sage-bot` (scopes: `repo`, `workflow`) |
| `GITHUB_BOT_LOGIN` | `media-sage-bot` |
| `GITHUB_BOT_EMAIL` | Bot account email |
| `GITHUB_BOT_NAME` | `media-sage-bot` |
| `GITHUB_WEBHOOK_SECRET` | Same secret registered in GitHub repo webhook settings |
| `JIRA_EMAIL` | `micgon7@gmail.com` |
| `JIRA_API_TOKEN` | Atlassian account API token |
| `PORT` | `8081` |

Webhook URLs (stable Railway URL — no ngrok required):
- Jira: `https://<railway-agent-url>/webhook/jira`
- GitHub: `https://<railway-agent-url>/webhook/github`

Register the Jira webhook at **media-sage.atlassian.net → Settings → System → WebHooks**:
- Events: Issue **created** and **updated**
- JQL filter: `project = MS AND labels = autonomous`

Register the GitHub webhook in repo **Settings → Webhooks**:
- Content type: `application/json`
- Events: `Pull request reviews`, `Pull request review comments`

**Level 3 & 4 setup (laptop — local dev/demo):**

For local development only (not needed when container is running):
1. Add `export AGENT_REPO_PATH="/path/to/media-sage"` to `~/.zshrc` and `source ~/.zshrc`
2. Start the agent server: `source ~/.zshrc && ./gradlew :agent:run`
3. Start ngrok: `ngrok http 8081` — copy the public HTTPS URL
4. Temporarily update Jira and GitHub webhook URLs to the ngrok URL

See `docs/MS-78-level-4-github-webhook.md` for full details and enterprise notes.
See `docs/MS-69-level-3-autonomous-agent.md` for Level 3 setup details.
See `docs/MS-84-containerized-agent-deployment.md` for container architecture and Railway setup.

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
