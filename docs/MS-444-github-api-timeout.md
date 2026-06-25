# MS-444: GitHub API Timeout in Analyst Auto-PR Flow

**Epic:** MS-5 (Agentic Pipeline)
**Date completed:** 2026-06-25

## What was built

Diagnosed and fixed a `HttpRequestTimeoutException` that consistently prevented the analyst's auto-PR feature from opening PRs. Added retry logic with per-attempt timeouts to `GitHubAppClient.installationToken()` and graceful error handling in `SkillPrService.maybeOpenPr()`.

### Files changed
- **`analyst/.../github/GitHubAppClient.kt`** — `installationToken()` retries up to 3× with a 15s per-attempt ceiling; `IOException` triggers a retry; non-2xx responses propagate immediately
- **`analyst/.../pr/SkillPrService.kt`** — `hasOpenAnalystPr()` call wrapped with separate `CancellationException` re-throw + broad `Exception` catch to prevent a GitHub failure from killing the background coroutine
- **`analyst/.../GitHubAppClientJwtTest.kt`** — two new tests: retry-on-failure-then-succeed, exhaust-all-retries-throws
- **`analyst/.../SkillPrServiceTest.kt`** — new test for GitHub check throwing; `FakeGitHubApiClient` gains `throwOnHasOpenPr` flag

## Root cause

The analyst's Pub/Sub webhook handler responds 200 immediately, then launches a background coroutine:

```kotlin
call.application.launch {
    decisionScorer.score(jobId)
    skillPrService?.maybeOpenPr()   // ← happens here
}
```

`DatabasePatternDetector.detectPatterns()` runs two Supabase queries over a re-established JDBC connection. In practice this takes ~4–5 minutes (cold pool on a min-instances=0 Cloud Run service). By the time the GitHub call fires, the 60-second global `requestTimeoutMillis` on the shared `HttpClient` has already elapsed — so the very first byte sent to GitHub's API exceeds the budget, throwing `HttpRequestTimeoutException`.

## Key decisions & why

- **Per-request timeout override (`timeout { requestTimeoutMillis = 15_000 }`) instead of raising the global timeout**: The global timeout protects all other calls. GitHub's access-token endpoint responds in <1s under normal conditions — a timeout there indicates a transient network hiccup, not a slow server. 15s is generous; 60s was just wrong for a post-DB-query context.
- **Retry only on `IOException`**: `HttpRequestTimeoutException` extends `IOException` in Ktor 3.x, so the catch clause covers it without special-casing. Non-2xx responses throw `IllegalStateException` (via `check()`) and propagate immediately — retrying a 401 would be pointless.
- **3 attempts, warn on attempts 1 and 2**: One retry absorbs a single transient hiccup; a second gives confidence before giving up. Logging the attempt number makes the retry sequence visible in Cloud Run logs without being noisy on success.
- **`error(...)` after all retries exhausted**: Surfaces as `IllegalStateException` with a clear message. The `runCatching { openPr(...) }` in `maybeOpenPr()` catches it and logs `.onFailure` — the background coroutine does not crash.
- **Separate `CancellationException` catch block in `SkillPrService`**: Detekt's `InstanceOfCheckForException` rule requires separate catch clauses instead of `if (e is CancellationException) throw e` inside a broad catch. More importantly: swallowing `CancellationException` breaks coroutine cooperative cancellation — the re-throw is correctness, not style.

## Concepts learned

- **`HttpRequestTimeoutException` is an `IOException`** in Ktor 3.x. You can catch `IOException` to handle both socket errors and Ktor coroutine-level timeouts uniformly.
- **Per-request timeout override**: `timeout { requestTimeoutMillis = N }` inside a request builder overrides the plugin-level default for that single call. Use it when one call in a flow has meaningfully different latency characteristics from the others.
- **Background coroutines and wall-clock time**: A `launch {}` that fires after a long-running suspend (DB queries, Claude calls) may encounter a different network budget than the code assumes. Consider the total elapsed time before any network call, not just the call itself.
- **`CancellationException` must be re-thrown**: Catching `Exception` without re-throwing `CancellationException` breaks coroutine cancellation. Always handle it in its own `catch` block before the broad handler.
- **Ktor `MockEngine` and `IOException`**: A `MockEngine` handler can `throw IOException(...)` directly — no `respondError()` needed — to simulate a timeout or connection reset in tests.
