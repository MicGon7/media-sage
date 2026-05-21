# MS-210 — Track Token Usage and Cost per Worker Run

## What Was Built

Added per-execution efficiency metrics to the `jobs` table. After every Cloud Run worker
completes, the orchestrator reads the Claude Code `result` event from Cloud Logging, parses
token counts and cost, and stores them alongside the `COMPLETED` status.

**New columns on `jobs`:**

| Column | Type | Source |
|---|---|---|
| `input_tokens` | INT | `usage.input_tokens` |
| `output_tokens` | INT | `usage.output_tokens` |
| `cache_read_tokens` | INT | `usage.cache_read_input_tokens` |
| `cache_creation_tokens` | INT | `usage.cache_creation_input_tokens` |
| `total_cost_usd` | NUMERIC(10,6) | `total_cost_usd` |
| `claude_duration_ms` | BIGINT | `duration_ms` |
| `num_turns` | INT | `num_turns` |

All columns are **nullable** — pre-MS-210 rows and runs where log fetch fails degrade
gracefully. The job is never blocked or failed due to missing metrics.

## Architecture Decision: Orchestrator Reads Cloud Logging

The worker (Claude Code subprocess) writes its stream-json output to stdout. Cloud Run
captures stdout into Cloud Logging automatically. After the LRO signals completion, the
orchestrator fetches those logs to extract the `result` event.

**Why not have the worker write metrics directly to the DB?**

The worker's job is to execute work, not to know how it's being measured. Giving the worker
DB credentials and metric-reporting responsibility couples it to infrastructure concerns it
shouldn't own. The orchestrator already owns the full job lifecycle (PENDING → RUNNING →
COMPLETED) — recording cost at completion is a natural extension of that responsibility.

This is the "manager reads the records" pattern. The worker never changes regardless of
what metrics we decide to track.

## The Claude Code `result` Event

Every `claude -p ... --output-format stream-json --verbose` run ends with a single line:

```json
{
  "type": "result",
  "total_cost_usd": 1.2345,
  "duration_ms": 1234567,
  "num_turns": 42,
  "usage": {
    "input_tokens": 10000,
    "output_tokens": 2000,
    "cache_read_input_tokens": 8000,
    "cache_creation_input_tokens": 500
  }
}
```

This is the single source of truth for cost and token usage. No scraping required.

## Cloud Logging Ingestion Latency

Cloud Logging has 5–15 seconds of ingestion latency after a container exits. `CloudLoggingClient`
retries 5 times: 15-second initial delay, 10-second retry interval. If the result event is
still not found after 5 attempts, metrics remain null and the job row is still marked
`COMPLETED`. This is graceful degradation, not a failure.

## Pub/Sub Compatibility (MS-201)

`CloudLoggingClient` is a pure function: `executionName → WorkerMetrics?`. It has no
knowledge of how job completion was detected. When LRO polling is replaced by Pub/Sub
events in MS-201, only the call site in `CloudRunJobsClient.handleDone()` changes — this
client is unchanged.

## What Changed

### New files
- `agent/src/main/kotlin/com/mediasage/agent/db/WorkerMetrics.kt` — data class
- `agent/src/main/kotlin/com/mediasage/agent/service/CloudLoggingClient.kt` — log fetcher and parser
- `agent/src/test/kotlin/com/mediasage/agent/CloudLoggingClientTest.kt` — 5 unit tests

### Modified files
- `AgentDatabase.kt` — idempotent `ALTER TABLE IF NOT EXISTS` migration; extracted `migrate()` helper
- `JobsTable.kt` — 7 new nullable column definitions
- `JobRegistry.kt` — `markCompleted(UUID, WorkerMetrics? = null)` signature
- `JobRepository.kt` — writes metric columns when present
- `CloudRunJobsClient.kt` — injects `CloudLoggingClient`, calls it in `handleDone()`
- `AgentModule.kt` — wires `CloudLoggingClient`

## Incident: Railway Deployed Feature Branch

When `feature/MS-210-worker-metrics` was pushed to GitHub, Railway auto-deployed it
(Railway watches all branches by default, not just main). Gradle's build cache caused a
binary incompatibility: `CloudRunJobsClient` was served from the old cached build
(calling `markCompleted(UUID)`) but `JobRepository` was recompiled fresh with the new
signature (`markCompleted(UUID, WorkerMetrics?)`). This produced a `NoSuchMethodError`
that crashed the orchestrator mid-poll after the MS-180 run completed.

**Fix applied:** Merging MS-210 to main deploys both files consistently.

**Lesson:** Configure Railway's `:agent` service to watch only the `main` branch, not all
branches. Branch-scoped deployment prevents feature branch pushes from poisoning production.

## Key Learnings

1. **The `result` event is the authoritative cost record** — one line per run, all token
   counters in one place. No need to aggregate across assistant turns.

2. **Cloud Logging latency requires retry logic** — logs are not immediately available
   after container exit. A 15-second initial delay with 10-second retries handles the
   typical 5–15 second ingestion window.

3. **Inject `tokenProvider` for testability** — `GoogleCredentials.refreshIfExpired()` makes
   a real HTTP call to Google's auth endpoint. Tests must bypass this with an injectable
   `tokenProvider: (() -> String)?` parameter. The production path leaves it null and uses
   real GCP credentials.

4. **Railway watches all branches by default** — pushing any branch to GitHub triggers a
   Railway redeploy. Set the branch filter in Railway service settings to `main` to prevent
   feature branches from deploying to production.

5. **Gradle build cache + partial recompile = binary incompatibility** — when a Kotlin
   interface signature changes, all callers must be recompiled together. Gradle's incremental
   compilation handles this within a single clean build, but cached artifacts across
   deployments can cause mismatches. Consistent full deployments from a single source commit
   prevent this.
