# MS-444: GitHub API Timeout in Analyst Auto-PR Flow

**Epic:** MS-5 (Agentic Pipeline)
**Date completed:** 2026-06-25

## What was built

Diagnosed and fixed a `HttpRequestTimeoutException` that consistently prevented the analyst's auto-PR feature from opening PRs. Added retry logic with per-attempt timeouts to `GitHubAppClient.installationToken()` and graceful error handling in `SkillPrService.maybeOpenPr()`.

### Files changed
- **`analyst/.../github/GitHubAppClient.kt`** — `installationToken()` adds `timeout { requestTimeoutMillis = 15_000 }` to override the global 60s ceiling for this one call
- **`analyst/.../pr/SkillPrService.kt`** — `hasOpenAnalystPr()` wrapped with `runCatching`, consistent with the existing `runCatching { openPr(...) }` pattern already in the file
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

- **Per-request timeout override, not a retry loop**: The root cause was a misconfigured timeout, not an unreliable endpoint. GitHub's access-token endpoint responds in <1s — if it times out, there's no reason to believe a second attempt will behave differently. One line (`timeout { requestTimeoutMillis = 15_000 }`) fixes the problem without adding retry infrastructure.
- **`runCatching` for `hasOpenAnalystPr()`**: Consistent with `runCatching { openPr(...) }` already used below in the same function. If the GitHub check fails, the right behavior is to skip — the same as if an open PR already existed.

## Concepts learned

- **Per-request timeout override**: `timeout { requestTimeoutMillis = N }` inside a request builder overrides the plugin-level default for that single call. Use it when one call in a long-running flow has meaningfully different timing constraints than the others.
- **Background coroutines and wall-clock time**: A `launch {}` that fires after a long-running suspend (DB queries, Claude calls) may have exhausted the HTTP client's timeout budget before it even starts. Consider total elapsed time, not just the time for the call itself.
- **Fix the root cause, not the symptom**: The instinct to add retries was treating the timeout as an unreliable endpoint problem. The real problem was a misconfigured timeout. Match the fix to the actual failure mode.
