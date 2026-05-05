# MS-136: Split monorepo — :agent and :scripts extracted from :server

## Background

`:server` was carrying three unrelated responsibilities:
- App API (deployed to Railway, port 8080)
- Webhook orchestration (spawns Claude Code workers, runs locally, port was 8080)
- Portrait generation scripts (offline batch jobs, gpt-image-2)

This made it unclear what `:server` does in production, blocked containerizing the agent, and mixed concerns with very different deployment lifecycles.

## What Changed

The monorepo now has five modules with single responsibilities:

| Module | Responsibility | Port |
|---|---|---|
| `:composeApp` | Compose Multiplatform UI | — |
| `:shared` | KMP data layer | — |
| `:server` | App API, Railway deployment | 8080 |
| `:agent` | Webhook orchestration, agent spawning | 8081 |
| `:scripts` | One-off batch jobs | — |

## :agent module

Files moved from `:server` to `:agent` (package updated `server` → `agent`):
- `routes/JiraWebhookRoutes.kt`
- `routes/GitHubWebhookRoutes.kt`
- `service/AgentLaunchService.kt`
- `service/JiraApiService.kt` (includes `JiraLabelChecker` interface)

Files absorbed: `di/JiraConfig.kt` → replaced by `di/AgentConfig.kt` (adds `githubWebhookSecret`)

New file: `tools/ToolDefinitions.kt` — canonical registry of agent behaviors as Anthropic-idiomatic tool definitions (JIRA_TICKET_AGENT, PR_REVIEW_AGENT). Follows the orchestrator-worker pattern: define the tool first, then wire the route.

`application.conf` listens on port 8081. Only has `app.agent`, `app.github`, and `app.jira` config blocks.

`ServerModule` simplified to: `serverModule(claudeApiKey, newsApiKey, scriptureApiKey, baseUrl)` — no agent or Jira params.

## :scripts module

`ImageGenerationService.kt` moved from `:server/service` to `:scripts/service`.
`GenerateFigureImages.kt` moved from `:server/scripts` to `:scripts`.

Added `ScriptsDatabase.kt` — self-contained Exposed DB accessor for the figures table. Keeps `:scripts` fully independent (no dependency on `:server`).

`generateImages` Gradle task moved from `server/build.gradle.kts` to `scripts/build.gradle.kts`.

## Dead code removed from :server

- Deprecated `matchRoute()` in `AnalysisRoutes.kt` and its `@Suppress("DEPRECATION")` call site
- Deprecated `matchQuoteToHeadline()` method and `MATCH_SYSTEM_PROMPT` constant in `ClaudeApiService.kt`
- `QuoteCandidate` and `MatchResult` data classes (used only by the deprecated method)
- `ClaudeApiServiceTest.kt` — entirely tested the deprecated method, deleted
- `AnalysisRoutes.kt` renamed to `EncourageRoutes.kt` to match the single public class (`EncourageRequest` made private to satisfy detekt `MatchingDeclarationName` rule)

## ngrok setup change

`:agent` runs on port 8081. Webhook URLs are now:
- Jira: `https://<ngrok-8081-url>/webhook/jira`
- GitHub: `https://<ngrok-8081-url>/webhook/github`

Run `:server` and `:agent` in separate terminals:
```bash
source ~/.zshrc && ./gradlew :server:run   # port 8080 — app API
source ~/.zshrc && ./gradlew :agent:run    # port 8081 — webhooks
```

## Future

`:agent` runs on the developer's laptop today. The next step is moving it to a container so it can run 24/7 without a local machine. The single-responsibility module boundary makes that straightforward — `:agent` has no DB deps and no app API concerns.
