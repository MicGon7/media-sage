# Media Sage — Development Guide

## Project Overview

Media Sage (public name: **The Media Sage**) is a Kotlin Multiplatform (KMP) app with a Ktor backend that matches news headlines with encouraging quotes from Christian theologians, mystics, and biblical figures using Claude AI. It targets Android, iOS, and a JVM server.

## Architecture

Five-module Gradle project (`settings.gradle.kts`):

```
:composeApp   — Compose Multiplatform UI (Android + iOS)
:shared       — KMP library (networking, database, domain models)
:server       — Ktor app API, deployed to Railway (port 8080)
:agent        — Ktor orchestration server, deployed as Cloud Run Service on GCP (port 8081)
:scripts      — One-off batch jobs, run manually (no server, no Koin wiring)
```

### Module Responsibilities

- **composeApp**: UI layer only. Depends on `:shared`. Uses Compose Material3, Koin for DI, Lifecycle ViewModel, and Nav3 for navigation.
- **shared**: Business logic, data layer, networking. Room for persistence, Ktor Client for HTTP, kotlinx-serialization for JSON. Platform engines: OkHttp (Android), Darwin (iOS).
- **server**: JVM-only Ktor server (Netty). Calls external APIs (Claude, News, Scripture). Uses Koin for DI, CORS, StatusPages, ContentNegotiation, CallLogging. Deployed to Railway.
- **agent**: JVM-only Ktor server (Netty, port 8081). Receives Jira and GitHub webhooks, dispatches Claude Code workers via Cloud Run Jobs. Uses Exposed + PostgreSQL (Supabase) for persistent job state. Deployed as a Cloud Run Service on GCP (`media-sage-orchestrator`, `us-central1`). Railway agent service is kept as a manual fallback (deactivated; re-enable by redeploying and updating webhooks).
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
- **Server**: `serverModule(claudeApiKey, newsApiKey, scriptureApiKey, baseUrl)` — HttpClient, API services
- **Agent**: `agentModule(config, scope)` — HttpClient, AgentLaunchService, JiraApiService
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

server/src/main/kotlin/com/mediasage/server/
├── Application.kt       — Entry point, Koin setup
├── plugins/             — ContentNegotiation, CORS, CallLogging, StatusPages
├── routes/              — Health, News, Encourage, Scripture, Figures, DailyReflection
├── service/             — ClaudeApiService, NewsApiService, ScriptureApiService
└── di/                  — ServerModule

agent/src/main/kotlin/com/mediasage/agent/
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
source ~/.zshrc && ./gradlew :server:run

# Run agent orchestration server locally (port 8081 — requires Jira, GitHub env vars)
source ~/.zshrc && ./gradlew :agent:run

# Build agent container image locally
docker build -f agent/Dockerfile -t media-sage-agent .

# Build worker image for Cloud Run (automated via .github/workflows/build-worker-image.yml on merge to main)
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

### Code
- Kotlin code style: `official` (set in `gradle.properties`)
- JVM target: 11
- Use `kotlinx.serialization` for all JSON — no Gson/Moshi
- Use Ktor client in shared, Ktor server in server — never mix
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
- Smoke test external API changes with real APIs before creating a PR
- **PR body format**: pre-check unit test items with `[x]` (already verified before push); tests that require a live deployed system go in a separate `## Post-deploy verification` section; omit the test plan section entirely for PRs with no live smoke test
- **Pipeline E2E scenarios** (`:pipelineScenarios`): on-demand health checks run via `./gradlew :pipelineScenarios:e2e*`. Never run in standard CI — they dispatch real Cloud Run Jobs. `e2eDedupCompleted` is the post-deploy canary (Supabase only, no Cloud Run).

### Quality Gates
- **Detekt**: Runs in CI before build. `./gradlew detekt` must pass.
- **Kover**: Coverage reports generated in CI, uploaded as artifacts. Target: 70% line coverage (Phase 2).

## Agent Guidelines

Each job type the pipeline can execute has its own skill in `.claude/commands/`. The three-part model:

- **CLAUDE.md** — rules (standing constraints that apply across all jobs)
- **Prompt** — context (job-specific: ticket key, PR number, branch, comment text)
- **Skill** — instructions (how to execute the job — branch, implement, test, PR, jira comment)

Workflow steps live in skills, not here. See `.claude/commands/` for the full instruction set for each job type.

### Rules

- **Tests:** Run `./scripts/run-affected-tests.sh` inside the container (Linux, no Android/iOS SDK). Never run bare `./gradlew :module:test` directly. If the script skips for any reason, do not run any Gradle test task manually — CI is the authoritative quality gate.
- **Blocker stop rule:** If a required tool, SDK, or Gradle task is missing and cannot be self-resolved, **stop immediately**. Post a comment on the PR or Jira ticket describing the exact blocker, then exit.
- **OOM stop rule:** If any Gradle command exits with an out-of-memory error, Gradle daemon startup failure, or cgroup memory limit error — **stop immediately**. Do not investigate daemon logs, run diagnostics, or retry with alternative JVM flags. Post a comment stating that Gradle quality gates are blocked by an environment memory constraint and that CI is the authoritative quality gate, then exit.
- **No secrets:** No API keys or secrets in code — use environment variables.
- **Never push to main:** Always create a PR. Never merge a PR — human reviews and merges.
- **Smoke test external APIs:** Test real API changes with live APIs before writing the learning doc or opening a PR — docs describe verified behaviour, not assumed behaviour.
- **Jira comment file:** Every job writes a plain-text summary to `/tmp/jira_comment.txt` before exiting. No bold markdown. Do NOT post via the Atlassian MCP — the orchestrator reads this file from the Pub/Sub completion event and posts it as Media Sage Bot. Each skill defines what content to include; the format rules are always: plain text, pipeline checkpoints where relevant, PR URL, quality gate results, AC summary.

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

An **autonomous agent** runs the full workflow — Jira, branch, code, tests, docs, commit, PR — without human interaction. The human's only touchpoint is the GitHub PR review.

**Jira labels that control agent behavior:**

- No label — human work with no AI involvement.
- **`assisted`** — Human and AI work together. Human stays in the loop, approves decisions, and learns the patterns. AI acts as a pair programmer. This is the default for all AI-intended tickets and the expected minimum standard for the team.
- **`autonomous`** — Explicit upgrade from `assisted`. AI works alone end-to-end, human only reviews the PR. Only appropriate for patterns that are already proven and well-understood.

`assisted` is always the starting point. Promote to `autonomous` only after the pattern has been built and validated in `assisted` mode. When in doubt, stay `assisted`.

**Trigger model (Level 2 — Autonomous):** The Jira webhook fires when a ticket is **assigned to the bot account** and its status transitions to **In Progress**. The `autonomous` label is a documentation tag for filtering and reporting — it is NOT what fires the agent. The GitHub webhook fires when a `pull_request_review` or `pull_request_review_comment` event arrives for a branch whose ticket key maps to an `autonomous`-labeled issue in Jira.

**Invocation:**
```bash
# Assisted — human approves tool calls (interactive Claude Code session)
claude

# Autonomous — bootstrap command (always the same, ticket is the prompt)
claude -p "Check Jira (cloudId: media-sage.atlassian.net) for the next ticket labeled 'autonomous' in project MS with status 'To Do'. Read the ticket description and acceptance criteria for your task. Follow the Agent Guidelines in CLAUDE.md and execute the full workflow." \
  --dangerously-skip-permissions
```

The bootstrap command never changes — the **ticket is the prompt**. Every autonomous ticket must include a clear task description and explicit acceptance criteria so the agent has everything it needs without human input.

**Job-type skills:** Each job type dispatched by the orchestrator ends its bootstrap prompt with a skill invocation (e.g. `/conflict-resolution`). Skills live in `.claude/commands/` and are committed to the repo — workers pick them up automatically on clone. The skill contains the imperative workflow steps for that job type; the bootstrap prompt supplies the job-specific context (branch, ticket, PR number). This separates *what the job is* (prompt) from *how to execute it* (skill), and allows workflow steps to be updated without redeploying the orchestrator image.

Current skills:
- `/conflict-resolution` — rebase a branch ejected from the merge queue and re-request review
- `/ticket-work` — execute the full ticket work workflow (branch, implement, test, detekt, PR, Jira comment)
- `/pr-review` — respond to a PR review comment: fix code (or explain why not), push, re-request review
- `/pr-comment` — answer a conversational PR comment via a reply; no code push

**Autonomous ticket requirements:**
- Title: concise task description (agent uses this as the task summary)
- Description: what needs to change and why
- Acceptance criteria: explicit checkboxes the agent checks off as it works
- Relevant files: **mandatory** — list the 3–5 files the agent should read first, each with a one-line note on why it matters. This is the primary way context is passed to the worker; the briefing skips file enumeration entirely and relies on this section being present. A ticket without a relevant files section is not ready for autonomous mode.
- Acceptance criteria: describe **outcomes, not commands** — Haiku reads AC as briefing input, so shell commands in AC leak into the dispatch prompt and conflict with the `/ticket-work` skill. Good: "The foo field is validated at the repository boundary." Bad: "Run `./gradlew :shared:test`."
- Label: `autonomous`
- No ambiguous requirements — if it needs clarification, use `assisted` instead
- Tickets that touch `.github/workflows/`, `Dockerfile.worker`, or `agent/worker-entrypoint.sh` must use `assisted` — these files define the pipeline itself, the worker cannot push workflow files without elevated permissions, and mistakes here have wide blast radius

**Automation levels:**

- **Level 1 — Assisted**: Human works interactively with Claude Code in any configuration (auto-accept, plan mode, or with tool approvals). The configuration doesn't define the level — the human's presence does. They can steer, redirect, and co-author at any point. This is AI-augmented pair programming.
- **Level 2 — Autonomous**: Jira webhook fires when a ticket is assigned to the bot account and moved to In Progress. The orchestrator dispatches a Cloud Run Job. The worker runs the full workflow autonomously. The human's only touchpoint is the PR review.

  The full PR lifecycle is part of Level 2 — not a separate level:
  - **PR review comments** → GitHub webhook → orchestrator dispatches a worker → fix commit + re-request review
  - **Merge queue conflict** → GitHub webhook → orchestrator dispatches a worker → rebase + re-request review

  The human's touchpoint (PR review) never changes regardless of how many review cycles occur.

_This project is at Level 2. Both the Jira webhook (`POST /webhook/jira`) and the GitHub webhook (`POST /webhook/github`) are live in the `:agent` module, deployed as a Cloud Run Service on GCP. See `docs/diagrams/agent-pipeline.md` for the full flow diagram._

See `agent/CLAUDE.md` for deployment config, env vars, webhook URLs, job registry schema, and local dev setup.

**Autonomous vs Assisted:**

- **Human touchpoints**: Autonomous = PR review only. Assisted = human present throughout.
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

## MCP Servers

These MCP servers are pre-configured for this project and available in every assisted session. Use them instead of making raw API calls or running CLI commands manually.

### Project-local (`.claude.json`)

| Server | Transport | Purpose |
|--------|-----------|---------|
| `atlassian` | SSE → `https://mcp.atlassian.com/v1/sse` | Jira (tickets, transitions, comments) + Confluence (impact page updates) |
| `railway` | HTTP → `https://mcp.railway.com` | Deployment history, service logs, restart counts, environment variables |
| `gcloud-mcp` | stdio `npx -y @google-cloud/gcloud-mcp` | Cloud Run job management, GCP resource queries |
| `observability-mcp` | stdio `npx -y @google-cloud/observability-mcp` | Cloud Run execution logs, job duration metrics, cost data |

**Setup commands** (run once per machine from the project root):
```bash
claude mcp add railway --transport http https://mcp.railway.com
claude mcp add gcloud-mcp -- npx -y @google-cloud/gcloud-mcp
claude mcp add observability-mcp -- npx -y @google-cloud/observability-mcp
```

Railway authenticates via OAuth on first use (browser prompt). GCP MCPs inherit the active `gcloud auth` session — run `gcloud auth login` first if needed.

### Account-level (claude.ai MCP marketplace)

These are connected at the account level and available across all projects:

| Server | Purpose |
|--------|---------|
| Atlassian (claude.ai) | Redundant Jira/Confluence access via claude.ai OAuth |
| Mermaid Chart | Generate architecture diagrams |
| Excalidraw | Whiteboard-style diagrams |
| Figma | Design file access (currently failing — known auth issue) |

### Intended uses

- **Jira workflow**: use `atlassian` MCP tools instead of `gh` CLI or manual curl
- **Railway deploys**: use `railway` MCP to check service status and logs after a PR merges
- **Cost reporting**: use `gcloud-mcp` + `observability-mcp` to pull Cloud Run job execution times and build cost documents for the orchestrator/worker pattern
- **Confluence updates**: use `atlassian` MCP to update the Agentic Development Impact page (ID: `163844`)

## Project Tracking

- Jira project: Media Sage (key: MS) at media-sage.atlassian.net
- Kanban board — no sprints, track time via In Progress → In Review → Done transitions
- Epics: MS-1 (Server API Layer), MS-2 (Shared Data Layer), MS-3 (App UI), MS-4 (Infrastructure)
- Auto-transition: Jira tickets move to Done on PR merge via GitHub Actions
- Board settings: found under the three-dot menu next to the project in the recents sidebar → Board
