# MS-399 — `env_startup_ms` on the jobs table

## What & why

Records the **environment startup time** of every worker run on the Supabase `jobs` table:
the wall-clock from dispatch to the worker container's first log line — i.e. Cloud Run cold
start + worker image pull.

The `jobs` table already records total wall-clock (`job_durations` view = `completed_at −
started_at`) and Claude agent time (`claude_duration_ms`, MS-210). The piece missing was the
~3–4 min that elapses *before the container runs any code* — invisible to the worker, since it
hasn't started yet. For short jobs that interval is the dominant cost. This column makes it a
recorded number so the Advisor (MS-380) can show the environment-vs-agent split, and so the
worker-image slim (MS-400) can be measured against a clean baseline rather than noisy total
duration (which is swamped by per-ticket agent variance).

The column is **nullable** — recovery paths and runs with no readable first-log entry degrade
gracefully (same convention as the MS-210 / MS-386 columns).

## The design question, and how it was decided

Two decisions, both made by narrowing scope under pushback rather than building the obvious thing.

### Why one number, not five phases
The first instinct was full per-phase instrumentation (env startup / clone / agent / ship /
pubsub). That was over-scoped. To **A/B-test an image slim**, total `job_durations` already
works *if you hold the workload fixed* (the smoke test, whose agent phase is near-constant). The
genuine gap was exactly **one** boundary — *when the worker process comes up* — which splits
total into "environment" vs "everything the worker did once up." Combined with the agent time we
already capture, that's the whole environment-vs-agent split from a single new value.

### Why the orchestrator derives it (no worker instrumentation)
The expensive phase elapses *before* the entrypoint runs, so the worker can't self-measure it.
But Cloud Run already records it: the timestamp of the container's first log line. `CloudLoggingClient`
already lists an execution's log entries `timestamp desc` to find the `result` event; the mirror
query — `timestamp asc`, `pageSize 1` — yields the first container log timestamp. Then:

```
env_startup_ms = first_container_log_timestamp − started_at   (started_at = dispatch, already in the DB)
```

So this is **orchestrator-only**: no extended Pub/Sub event, no worker-emitted timestamps, no
`Dockerfile.worker` change, no worker image rebuild. We read what GCP already records. The first
container log today is `entrypoint-common.sh`'s `Generating GitHub App installation token…`,
emitted before the git clone — a fine ≈container-start anchor without touching the worker.

Anti-patterns avoided: threading new fields onto the worker's completion event, and a five-column
schema for a question that needed one number.

## Where it's computed

`CloudRunJobsClient.handleSuccess` already fetches `WorkerMetrics` from Cloud Logging. It now also
calls `computeEnvStartupMs(ticketKey, executionName, startedAt)`, which fetches the first-log
timestamp and subtracts dispatch time (`startedAt`, threaded in from the job row via
`PubSubWebhookRoutes` → `onJobCompleted`). The result is passed to `markCompleted(jobId, metrics,
envStartupMs)` — the repository just **stores** it (no logic in the data layer). The subtraction
is `coerceAtLeast(0)` to absorb sub-second clock skew between hosts. The recovery path
(`handleDone`) has no dispatch timestamp on hand, so it passes `startedAt = null` and env startup
is simply not recorded for recovered jobs.

## Schema migration

Added via the idempotent `AgentDatabase.migrate()` block (`ALTER TABLE jobs ADD COLUMN IF NOT
EXISTS env_startup_ms BIGINT`), which runs on every orchestrator startup — exactly how MS-210 /
MS-386 added their columns. The `job_durations` view is extended to surface it for the Advisor.

## Files touched

- `:pipelineCore` — `JobsTable` (`env_startup_ms` column), `JobRegistry`/`JobRepository`
  (`markCompleted` gains `envStartupMs`; persists it; `JobDurationRow` + `getJobDurations` expose it)
- `:orchestrator` — `AgentDatabase` (migration + view), `CloudLoggingClient` (`fetchFirstLogTimestamp`,
  parameterized request body), `CloudRunJobsClient` (`computeEnvStartupMs`, thread `startedAt`),
  `PubSubWebhookRoutes` (pass `job.startedAt`)
- Test fakes — `markCompleted` signature updated in 3 `JobRegistry` fakes (orchestrator + analyst tests)

## Gotcha logged

Postgres `CREATE OR REPLACE VIEW` is **append-only**: it cannot rename, drop, or reorder existing
columns — you may only add new columns at the *end* of the select list. The first draft inserted
`env_startup_ms` mid-list (after `duration_seconds`), which would have failed the migration on a
live view. Fixed by appending it after `completed_at`.

## Verification

- `./gradlew :orchestrator:test :analyst:test :pipelineCore:test` — green, incl. new
  `CloudLoggingClient.fetchFirstLogTimestamp` assertions (parses the first entry's timestamp;
  null on empty entries, non-2xx, and unparseable timestamp).
- `./gradlew :orchestrator:detekt :analyst:detekt :pipelineCore:detekt` — green.
- **Coverage boundary (honest):** `computeEnvStartupMs` end-to-end and `JobRepository` persistence
  aren't unit-tested — `CloudRunJobsClient` is coupled to the concrete repository and `JobRepository`
  hits Postgres (no in-memory harness, consistent with the repo). The real value is confirmed
  post-deploy: `env_startup_ms` is non-null and ~cold-start-sized on the next live worker run.
