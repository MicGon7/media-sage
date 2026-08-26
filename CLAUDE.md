# Media Sage — Development Guide

## Project Overview

Media Sage (public name: **The Media Sage**) is a Kotlin Multiplatform (KMP) app with a Ktor backend that matches news headlines with encouraging quotes from Christian theologians, mystics, and biblical figures using Claude AI. It targets Android, iOS, and a JVM server.

## Architecture

Five-module Gradle project (`settings.gradle.kts`):

```
:composeApp   — Compose Multiplatform UI (Android + iOS)
:shared       — KMP library (networking, database, domain models)
:appServer    — Ktor app API, deployed to Railway (port 8080)
:agentruntime — Ktor orchestration server, deployed as Cloud Run Service on GCP (port 8081)
:scripts      — One-off batch jobs, run manually (no server, no Koin wiring)
```

### Module Responsibilities

- **composeApp**: UI layer only. Depends on `:shared`. Uses Compose Material3, Koin for DI, Lifecycle ViewModel, and Nav3 for navigation.
- **shared**: Business logic, data layer, networking. Room for persistence, Ktor Client for HTTP, kotlinx-serialization for JSON. Platform engines: OkHttp (Android), Darwin (iOS).
- **appServer**: JVM-only Ktor server (Netty). Calls external APIs (Claude, News, Scripture). Uses Koin for DI, CORS, StatusPages, ContentNegotiation, CallLogging. Deployed to Railway.
- **agentruntime**: JVM-only Ktor server (Netty, port 8081). Receives Jira and GitHub webhooks, dispatches Claude Code workers via Cloud Run Jobs. Uses Exposed + PostgreSQL (Supabase) for persistent job state. Deployed as a Cloud Run Service on GCP (`media-sage-orchestrator`, `us-central1`). Railway orchestrator service is kept as a manual fallback (deactivated; re-enable by redeploying and updating webhooks).
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

### Dependency Injection

Koin is used across all modules. Define modules per feature, not per layer.
- **appServer**: `serverModule(claudeApiKey, newsApiKey, scriptureApiKey, baseUrl, dailyClaudeCallLimit)` — HttpClient, API services
- **Orchestrator**: `agentModule(config, scope)` — HttpClient, AgentLaunchService, JiraApiClient
- **Shared**: `sharedModule(serverBaseUrl)` — HttpClient, MediaSageApi, repositories

See each module's `CLAUDE.md` for module-specific patterns and conventions.

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

appServer/src/main/kotlin/com/mediasage/appserver/
├── Application.kt       — Entry point, Koin setup
├── plugins/             — ContentNegotiation, CORS, CallLogging, StatusPages
├── routes/              — Health, News, Encourage, Scripture, Figures, DailyReflection
├── service/             — ClaudeApiClient, NewsApiClient, ScriptureApiClient
└── di/                  — ServerModule

agentruntime/src/main/kotlin/com/mediasage/agentruntime/
├── Application.kt       — Entry point, Koin setup (port 8081)
├── di/                  — AgentConfig, AgentModule
├── db/                  — AgentDatabase, JobsTable, JobRepository (Supabase Postgres)
├── plugins/             — ContentNegotiation, CallLogging, StatusPages
├── routes/              — JiraWebhookRoutes, GitHubWebhookRoutes
├── service/             — AgentLaunchService, CloudRunDispatch, CloudRunJobsClient, JiraApiService
└── tools/               — ToolDefinitions (Anthropic orchestrator-worker pattern)

pipelineScenarios/src/test/kotlin/com/mediasage/pipeline/
├── support/             — ScenarioConfig, ValidationReport, DedupScenarioBase, FullPipelineScenarioBase
├── dedup/               — DedupRunningE2eTest, DedupCompletedE2eTest, DedupFailedRetryE2eTest
└── pipeline/            — ConflictResolutionE2eTest, PrReviewResponseE2eTest, FailureRecoveryE2eTest

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
source ~/.zshrc && ./gradlew :appServer:run

# Run agent orchestration server locally (port 8081 — requires Jira, GitHub env vars)
source ~/.zshrc && ./gradlew :agentruntime:run

# Build agent container image locally
docker build -f agentruntime/Dockerfile -t media-sage-agent .

# Build worker image AND declaratively deploy the media-sage-agent-worker Cloud Run Job
# (automated via .github/workflows/build-worker-image.yml on merge to main — the job's SA,
# env vars, secrets, and sizing are declared in that workflow, mirroring deploy-orchestrator.yml)
# Manual build only needed when testing Dockerfile.worker changes locally before pushing:
docker build --platform linux/amd64 -f Dockerfile.worker \
  -t us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/worker:latest .
docker push us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/worker:latest

# Run agent container locally (replace values as needed)
docker run -p 8081:8081 \
  -e GITHUB_BOT_LOGIN="media-sage-worker[bot]" \
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

### Naming Conventions

#### Client vs Service

**Client** — a class that wraps a single `HttpClient` to communicate with one external API provider.
- No interface. `open` is **conditional, not the default** — Kotlin classes are `final` by design, so keep a Client `final` unless a test needs to subclass it. Declare it `open` (with `open suspend fun` methods) only when its behavior is exercised *through a service or coroutine under `runTest` + `advanceUntilIdle`*, where `MockEngine` would escape virtual time: `MockEngine` runs on `Dispatchers.IO`, so `advanceUntilIdle()` returns before HTTP work completes when the call happens inside a nested `launch`. In that case a no-IO subclass override is preferred over `MockEngine`. A Client tested directly with `MockEngine` — a suspend call awaited in the test with no nested `launch` — stays `final`. Positive example: `JiraApiClient` (agentruntime) is `open` and subclassed by `FakeJiraApiClient` / `RecordingJiraApiClient`, injected into `cloudRunService` and driven under `runTest` + `advanceUntilIdle`. Negative example: the appServer clients (`NewsApiClient`, `ScriptureApiClient`) are tested directly with `MockEngine` and correctly stay `final`.
- Named `{Provider}ApiClient` (e.g. `JiraApiClient`, `ClaudeApiClient`, `NewsApiClient`).
- Methods are thin HTTP calls: authenticate, serialize request, deserialize response, return result.

**Service** — a class that orchestrates multiple clients or repositories to serve a broader purpose.
- The coordinator layer on the server side — analogous to Repository on the Android client side. The roles invert: Android Repositories hold Clients + DAOs; Ktor Services hold Clients + Repositories.
- May have an interface when a no-op implementation is needed (e.g. disabled feature flag via Koin module swap).
- Named `{Domain}Service` with interface + concrete `{Impl/Provider}Service` (e.g. `AgentService` / `ClaudeAgentService`).
- Methods represent meaningful business operations that coordinate multiple clients.

Do not use the `Impl` suffix — Kotlin docs treat it as illustrative only, not a real convention.

### Code
- Kotlin code style: `official` (set in `gradle.properties`)
- JVM target: 11
- Use `kotlinx.serialization` for all JSON — no Gson/Moshi
- Use Ktor client in shared, Ktor server in appServer — never mix
- Room schemas stored in `shared/schemas/`
- `@SerialName` annotations on their own line above the property
- String resources in `composeResources/values/strings.xml` — no hardcoded strings in UI
- API keys stored as env vars in `~/.zshrc`, read via `application.conf`
- **Solve problems at the right layer.** Before adding logic to any layer, identify where that concern idiomatically belongs in Android/Kotlin development. Network timeouts belong in the HTTP client (OkHttp `readTimeout`, Ktor `HttpTimeout`), not the ViewModel. Data validation belongs at the repository boundary, not the UI. If you find yourself adding network or I/O mechanics to a ViewModel, stop and check the idiomatic pattern first.
- Before implementing any Compose effect or Android platform API, verify the approach against NowInAndroid or the official Compose docs. If you find yourself adding a null guard inside a `SideEffect`, you've chosen the wrong effect type.

### Testing
- Common tests in `commonTest` source sets
- Server tests use `ktor-server-test-host` with `testApplication { }` DSL
- Shared module has `ktor-client-mock` with `MockEngine` for HTTP tests
- composeApp tests in `commonTest` — test platform-independent logic
- Use `runTest` from `kotlinx-coroutines-test` for suspending test functions
- Every new feature must include tests — run `./gradlew allTests` before creating a PR
- **Test infrastructure is never a side effect.** New test dependencies, manifests, source sets, or
  Gradle test config are introduced only by a ticket whose deliverable *is* that infrastructure —
  never inside a feature ticket because a test you wanted to write needed them. If a feature's test
  requires missing infrastructure, skip that test and say so in the PR body.
- Smoke test external API changes with real APIs before creating a PR
- **PR body format**: pre-check unit test items with `[x]` (already verified before push); tests that require a live deployed system go in a separate `## Post-deploy verification` section; omit the test plan section entirely for PRs with no live smoke test
- **Pipeline E2E scenarios** (`:pipelineScenarios`): on-demand health checks run via `./gradlew :pipelineScenarios:e2e*`. Never run in standard CI — they dispatch real Cloud Run Jobs. `e2eDedupCompleted` is the post-deploy canary (Supabase only, no Cloud Run).

#### Unit test principles
- No mocking libraries — use Fakes (lightweight in-memory implementations of the interface)
- No `@RunWith` annotations — this project uses `kotlin.test`, not JUnit4
- No business logic in Fakes — they store and return; they do not compute
- All test files go in `commonTest`, not `androidTest` or `iosTest` — tests must run on all platforms
- **Fake names must be unique across the whole package, not just the file.** Unlike top-level
  functions, Kotlin top-level classes are NOT file-scoped on the JVM — `private class FakeSyncMetaDao`
  in two different files in the same package (e.g. two `*RepositoryTest.kt` files under
  `data/repository/`) compiles as a JVM classname collision (`Redeclaration`), not a harmless shadow.
  Before adding a Fake, `grep -rn "class Fake<Name>" shared/src/commonTest/` for existing Fakes of that
  DAO/repository in the same package — if one exists, suffix your new one for its sync/feature context
  (e.g. `FakeSyncMetaDaoForReflectionSync`) rather than reusing the bare name. This has caused two
  consecutive CI failures (MS-664, MS-666) from copy-pasting a `Fake{Dao/Repository}` from a prior
  `*RepositoryTest.kt` in the same package without renaming it.
- **No JDK-only `java.util.Map` extensions in `commonMain`/`commonTest`.** `replaceAll`, `putIfAbsent`,
  `merge`, `compute`, `computeIfAbsent`, `computeIfPresent`, `getOrDefault` on `MutableMap` exist only on
  the JVM target — they compile fine locally (Android) and pass Detekt, but fail
  `compileTestKotlinIosArm64`/`IosSimulatorArm64` with an unresolved-reference error, so they only surface
  in CI. Use portable equivalents instead: `map.getOrPut(key) { default }` (this one IS common-Kotlin),
  or `map.keys.toList().forEach { k -> map[k]?.let { v -> map[k] = transform(v) } }` for a
  `replaceAll`-style update. **This matters more than usual here:** `./scripts/run-affected-tests.sh`
  skips entirely in the worker's Linux container (no Android/iOS SDK), so a Fake's `commonTest` code is
  never actually compiled before push in assisted/autonomous mode — CI's macOS runner is the *first*
  compiler this code sees for any platform. Read new/edited Fake code back with this rule in mind before
  committing, since there is no local compile step to catch it.

#### UI test principles
- No Espresso — that is for View-based UI, not Compose
- No `@RunWith(AndroidJUnit4::class)` in `commonTest` — use `runComposeUiTest {}` (the Compose Multiplatform API)
- No ViewModel or Koin in test setup — pass state directly to the composable; screens are stateless
- No hardcoded strings in assertions — use `getString(Res.string.x)` to resolve string resources

### Quality Gates
- **Detekt**: Runs in CI before build. `./gradlew detekt` must pass.
- **Kover**: Coverage reports generated in CI, uploaded as artifacts. Target: 70% line coverage (Phase 2).

**Before writing any new Kotlin code**, read `detekt.yml` in the project root to understand the active rules and thresholds. Key constraints: `LongMethod` (30 lines), `TooManyFunctions` (20/file, 15/class), `MaxLineLength` (140), `ReturnCount` (4). Violating these causes a CI failure that requires a follow-up fix commit.

## Agent Guidelines

Each job type the pipeline can execute has its own skill in `.claude/commands/`. The three-part model:

- **CLAUDE.md** — rules (standing constraints that apply across all jobs)
- **Prompt** — context (job-specific: ticket key, PR number, branch, comment text)
- **Skill** — instructions (how to execute the job — branch, implement, test, PR, jira comment)

Workflow steps live in skills, not here. See `.claude/commands/` for the full instruction set for each job type.

### Rules

- **Detekt first:** Before writing any new Kotlin code, read `detekt.yml` in the project root. Key thresholds: `LongMethod` 30 lines, `TooManyFunctions` 20/file, `MaxLineLength` 140, `ReturnCount` 4. Extract helper functions proactively to stay within limits — fixing a detekt violation after the fact costs an extra commit.
- **Tests:** Run `./scripts/run-affected-tests.sh` inside the container (Linux, no Android/iOS SDK). Never run bare `./gradlew :module:test` directly. If the script prints a skip notice (no Android/iOS SDK, or nothing affected), that is a non-failure — do not run any Gradle test task manually, do not `Read`/`cat` the script source to interpret the skip, and do not narrate it (see "No narration between steps" below). CI is the authoritative quality gate.
- **Local repro before live runs:** Before proposing or shipping a fix to worker/container infrastructure (`Dockerfile.worker`, `scripts/capture-ui.sh`, `entrypoint*.sh`, Gradle invocation flags), reproduce the actual bug locally and verify the fix against that repro first. A live Cloud Run worker run costs real wall-clock and tokens — it is never the first verification step for an infra fix that can be reproduced with a local script/Gradle invocation. This does not replace the eventual live check (container/hardware differences can still hide issues, e.g. absolute wall-clock), but the live run should confirm a fix already proven locally, not discover whether it works at all.
- **Blocker stop rule:** If a required tool, SDK, or Gradle task is missing and cannot be self-resolved, **stop immediately**. Post a comment on the PR or Jira ticket describing the exact blocker, then exit.
- **OOM stop rule:** If any Gradle command exits with an out-of-memory error, Gradle daemon startup failure, or cgroup memory limit error — **stop immediately**. Do not investigate daemon logs, run diagnostics, or retry with alternative JVM flags. Post a comment stating that Gradle quality gates are blocked by an environment memory constraint and that CI is the authoritative quality gate, then exit.
- **No secrets:** No API keys or secrets in code — use environment variables.
- **Never push to main:** Always create a PR. Never merge a PR — human reviews and merges.
- **Smoke test external APIs:** Test real API changes with live APIs before writing the learning doc or opening a PR — docs describe verified behaviour, not assumed behaviour.
- **Jira comment file:** Every job writes `/tmp/jira_comment.txt` before exiting — see `.claude/commands/ticket-work.md` for the exact format.
- **Batch reads before writing:** Gather every file you need in one parallel `Read` batch before writing any code — or, when the files must be *discovered* rather than started from a known list, in at most 2–3 batched rounds (locating searches together, then every named file together; a later round only for files a prior round's imports or composition *revealed*). Any file you will edit — including docs like `CLAUDE.md` — must be read first, or the later `Edit` hits the "file has not been read yet" guard and costs a recovery Read + retry. For a symbol rename, `grep` the symbol first and read every referencing file (source, tests, and docs) in the batch. What wastes turns is single, reactive reads spread one-per-turn, each deciding the next; that also causes mid-implementation rewrites when critical context arrives late.
- **Cap open-ended exploration:** The "2–3 batched rounds" ceiling in the rule above applies doubly once you've exhausted the ticket's named/discoverable files and are exploring by pattern instead (e.g. "how is this UI widget used elsewhere," "what's the existing convention for X"). Two rounds of that kind of search is a hard stop — synthesize an answer from what you have and proceed, rather than issuing a third round of single, narrowing greps. Re-running the same or a near-identical query (e.g. searching for a symbol, then searching for it again with a different flag) counts against this cap even if the exact string differs. Never inspect a dependency's compiled/packaged artifact (unzipping a `.jar`, reading a library's bundled `.class`/`.kt` sources) to answer a "how does this API behave" question — grep the repo's own existing usage of that API first; if the repo has no usage, state the assumption and proceed rather than reverse-engineering the dependency. This pattern burned an entire ticket's context budget on MS-715 (FAILED, "Prompt is too long" after 102 turns) via undirected pattern-discovery well past this ceiling, including unzipping `kotlinx-coroutines-core` to check `flatMapLatest` semantics the repo's own tests already answered.
- **No Glob→Read round-trips:** If you need a file's content, call `Read` directly. Do not `Glob` or search to locate a file you could infer from the package structure or a path you already have — that is two turns where one suffices. Once you have read the relevant files, do not issue a second `Glob` over the same directory to double-check coverage — trust the reads you already have.
- **Trust your own writes:** Do not re-read a file you just edited. `Edit` and `Write` succeed or error — there is no silent corruption. Re-reading is a wasted turn.
- **Trust your operational inputs — do not verify them:** A worker job is a non-interactive Cloud Run Job, not an interactive session. Treat operational inputs (env vars, worker-script outputs) the way a shell script treats its arguments: use them directly, never inspect them first. After sourcing a worker script's output, proceed immediately to the next step — do not `cat` an env file, `echo` a var, or run any command whose only purpose is to confirm a previous step worked. The exit code is the signal. Exported env vars only live in the shell process that sourced them — a later, separate `Bash` call cannot see them — so a script a later step needs to reference should print what's needed directly rather than relying on the worker re-reading its own exports.
- **No narration between steps:** Never emit a text response between tool calls, or one that only summarizes progress, restates a tool's own output, or announces what's next — chain directly into the next tool call instead. Every text response is a billable API round-trip and no human is watching a headless job's session. A text response is appropriate only when a step fails or genuinely requires a decision.
- **No TodoWrite in worker jobs:** There is no human watching the session UI in a Cloud Run Job — a task list is invisible and adds no value. A job's skill defines the workflow; a parallel task list is redundant and wastes turns.

### After a PR is merged
Do not include tickets labeled `pipeline-test` or `smoketest` in the Confluence impact doc — these tickets exist to exercise the pipeline, not deliver product or infrastructure value.

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

See [`docs/autonomous-mode.md`](docs/autonomous-mode.md) for invocation commands, ticket requirements, automation levels, and when to use assisted vs autonomous.

## MCP Servers

See [`docs/mcp-setup.md`](docs/mcp-setup.md) for server list, setup commands, and intended uses.

## Project Tracking

- Jira project: Media Sage (key: MS) at media-sage.atlassian.net
- Kanban board — no sprints, track time via In Progress → In Review → Done transitions
- Epics: MS-1 (Server API Layer), MS-2 (Shared Data Layer), MS-3 (App UI), MS-4 (Infrastructure)
- Auto-transition: Jira tickets move to Done on PR merge via GitHub Actions
- Board settings: found under the three-dot menu next to the project in the recents sidebar → Board
