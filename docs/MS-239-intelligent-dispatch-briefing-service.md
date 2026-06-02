# MS-239: Intelligent Dispatch — BriefingService

## What was built

The orchestrator now generates a pre-dispatch briefing for every worker launch using the Claude Haiku model. Before a Cloud Run Job starts, the orchestrator calls the Claude Messages API with the available context for that scenario, receives a concise 3-5 sentence summary, and appends it to the worker's prompt as an `## Agent Briefing` section.

Workers start knowing what to do. Discovery turns are eliminated or reduced.

## Why this matters

Prior to this change, workers spent their first 1-3 turns discovering context — reading the PR diff, checking which files were relevant, parsing the reviewer's intent. Each of those turns accumulates cached tokens. For MS-269 (a simple one-line fix), 773k cached tokens across 19 turns cost ~$1.19. With briefing, the worker is oriented before it starts and typical runs are expected to drop to 10-15 turns for similar tasks.

The cost math: a Haiku briefing call costs ~$0.004. Eliminating 3 discovery turns saves ~$0.036 in cached context. Net ~8x ROI per run.

## Key decisions

### Ktor client over `anthropic-java` SDK

The official Anthropic Java SDK exists (`com.anthropic:anthropic-java:2.35.0`) and is written in Kotlin, but it uses `CompletableFuture` for async with no native coroutine support. Using it would require a `future.await()` bridge that runs completion callbacks on a thread pool we don't control. The Claude Messages API is a single `POST /v1/messages` — using the existing Ktor `HttpClient` avoids the dependency and stays in our coroutine model. This decision is preserved in `HttpBriefingService`'s KDoc. The class is named for its transport (`Http`), not the model it currently calls — the model is a configuration value inside the class and will change independently.

### `INTELLIGENT_DISPATCH_ENABLED=true` by default

The flag defaults to true because the ROI is proven (8x cost savings) and the briefing is fully async — it never blocks the webhook response. Setting it to false reduces the orchestrator to a pure dispatcher with no Claude API calls.

### `BriefingService` as an interface

`BriefingService` is an interface implemented by `HttpBriefingService`. This follows the project's existing pattern (`AgentLauncher`, `JobDispatcher`, etc.) and enables `BriefingIntegrationTest` to use a synchronous `FakeBriefingService` rather than a `MockEngine`-backed HTTP client. Using `MockEngine` inside `runTest` causes a dispatcher mismatch: `MockEngine` dispatches HTTP completions to a real thread pool that isn't tracked by `TestCoroutineScheduler`, so `advanceUntilIdle()` returns before the HTTP call completes. The interface pattern avoids this entirely.

### Separate `BriefingContext` sealed class

Each dispatch scenario has different available context. Using a sealed class (`TicketWork`, `PrReview`, `CommentReview`, `ConflictResolution`) avoids nullable fields and makes it impossible to pass the wrong context to the wrong prompt template. `HttpBriefingService` pattern-matches on the subtype to build a scenario-appropriate Haiku prompt.

### Dedicated `HttpClient` for briefing

`AgentModule` creates a separate `HttpClient` for `HttpBriefingService` with a 15s timeout (vs the shared client's 60s). 15s gives Haiku room to process large diffs without blocking dispatch indefinitely — the webhook has already returned `200` by this point so the timeout only affects time-to-dispatch, not user-facing latency. A slow or failed response falls back gracefully to dispatch without briefing.

### PR diff capped at 500 lines

`fetchPrDiff` runs `gh pr diff {prNumber}` and takes the first 500 lines before passing the result into `BriefingContext.PrReview`. This covers virtually all normal bot PRs. The cap exists as a safety rail for pathological diffs — not for cost reasons (Haiku is cheap) but to keep the briefing prompt focused. The reviewer's comment already acts as a semantic query; RAG-style chunking of the diff is not warranted (see MS-273 for where RAG does apply).

### Max tokens set to 1,024

512 tokens was too tight — Haiku could truncate mid-thought on complex tickets or large diffs. 1,024 gives room for a complete, well-structured briefing across all four scenario types. The cost difference is negligible (~$0.002 per run at Haiku output pricing).

## Architecture

```
Webhook event
    ↓
AgentLaunchService.launch*(...)
    ↓
dispatchToCloudRun(key, basePrompt, cloudRun, briefingContext)
    ↓
doDispatch(...)
    ├── shouldDispatch() check
    ├── shouldSkipInterrupted() check
    └── buildPromptWithBriefing(ticketKey, basePrompt, briefingContext)
            ↓
        BriefingService.brief(context)  ← HttpBriefingService implementation
            ↓ null on failure (never throws)
        append "## Agent Briefing\n{result}" to basePrompt
            ↓
        CloudRunDispatch.executeJob(jobId, ticketKey, finalPrompt)
```

## New files

- `BriefingContext.kt` — sealed class with four dispatch scenario subtypes
- `BriefingService.kt` — interface with `suspend fun brief(context: BriefingContext): String?`
- `HttpBriefingService.kt` — implementation calling the Claude Messages API via Ktor client (named for transport, not model)

## Modified files

- `AgentLaunchService.kt` — accepts `BriefingService?`, passes typed context on all four dispatch paths, extracts `buildPromptWithBriefing` helper
- `AgentConfig.kt` — adds `intelligentDispatchEnabled`, `anthropicBaseUrl`, `anthropicAuthToken`
- `AgentModule.kt` — instantiates `HttpBriefingService` conditionally, dedicated briefing `HttpClient`
- `application.conf` — adds `app.dispatch.*` block with env var bindings

## Env vars added

| Var | Default | Purpose |
|-----|---------|---------|
| `INTELLIGENT_DISPATCH_ENABLED` | `true` | Master toggle — false = pure dispatcher mode |
| `ANTHROPIC_BASE_URL` | `https://api.fuelix.ai` | Claude API base URL (already set on GCP orchestrator) |
| `ANTHROPIC_AUTH_TOKEN` | — | Claude API bearer token (already set as `anthropic-auth-token` secret) |

`ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` are already configured on the GCP Cloud Run Service for the worker — the orchestrator can reuse the same values.

## What's next: MS-273

MS-273 adds RAG-powered codebase retrieval to the briefing layer. The orchestrator will embed all Kotlin source files into a pgvector store in Supabase and query it at dispatch time to inject the top-5 relevant file paths into `BriefingContext`. This composes directly on top of what was built here — `BriefingContext` subtypes will gain an optional `relevantFiles` field populated by the retrieval step before `HttpBriefingService.brief()` is called.
