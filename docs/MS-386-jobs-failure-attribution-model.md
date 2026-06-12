# MS-386 — `failed_gate` + `model_version` on the jobs table

## What & why

Enriches the Supabase `jobs` table so the Analyst (MS-380) can answer two questions it
currently can't:

- **Which quality gate caused a failure?** (`failed_gate` — `compile` / `tests` / `detekt` / `ci`)
- **Which model ran the job?** (`model_version` — e.g. `claude-sonnet-4-5-20250929`)

Prerequisite for the failure-pattern detector (MS-389) and the Slack digest's "most common
failure gate" line (MS-388). Both columns are **nullable** — successful runs, pre-MS-386 rows,
and runs where a value is unavailable degrade gracefully (same convention as the MS-210 metric columns).

## The design question, and how it was decided

The two fields look symmetric but come from different places, so they're produced differently.

### `model_version` — rides the existing metrics path (no new mechanism)
The orchestrator already pulls the Claude Code `result` event from Cloud Logging into
`WorkerMetrics` (MS-210). That event reports usage **per model** under `modelUsage`, keyed by
model name. A worker session runs a single model, so the first key *is* the model that ran.
So `model_version` is just one more field on `WorkerMetrics`, extracted in `CloudLoggingClient`
and persisted in `markCompleted` — grouped with its siblings (tokens, cost, turns).

### `failed_gate` — the worker is the producer
There was **no existing signal** for "which gate failed." A job's success/failure is only
Claude Code's exit code; gates are run by the agent during its turns, and CI runs on GitHub
*after* the PR (when the row is already `COMPLETED`). So this had to be built — and the decision
(made with the human) was: **the worker announces it, on the event it already publishes.**

This follows the pipeline's established principle (MS-201): *the producer owns the event; logs
are observability, not control flow.* The worker is the only thing that knows why it failed, so
it says so — rather than the orchestrator scraping logs or CI to infer it. Concretely:

1. **Worker** (standing rule in root `CLAUDE.md`): on an unrecoverable gate failure, write the
   gate name to `/tmp/failed_gate.txt` before exiting.
2. **`entrypoint-common.sh`** `publish_completion`: on `status=failure`, read that file and add
   `failedGate` to the **one** terminal Pub/Sub `JobCompletionEvent` it already emits (no second event).
3. **Orchestrator** (`PubSubWebhookRoutes` → `onJobCompleted`): thread `failedGate` into
   `markFailed(jobId, failedGate, modelVersion)`, which persists it. Model is also captured
   best-effort on the failure path so `model_version` is recorded per run, not only on success.

Anti-patterns explicitly avoided: a separate "failure-detail" event (two writes, ordering races),
and consumer-side log inference (the thing MS-201 moved away from).

## Schema migration

Both columns are added via the idempotent `AgentDatabase.migrate()` block
(`ALTER TABLE jobs ADD COLUMN IF NOT EXISTS …`), which runs on every orchestrator startup —
exactly how MS-210 added its columns. No manual Supabase SQL.

## Files touched

- `:pipelineCore` — `JobsTable` (2 columns), `WorkerMetrics` (`modelVersion`),
  `JobCompletionEvent` (`failedGate`), `JobRegistry`/`JobRepository` (`markFailed` signature +
  persistence, `markCompleted` persists model)
- `:orchestrator` — `AgentDatabase.migrate()` (ALTER), `CloudLoggingClient` (parse model from
  `modelUsage`), `CloudRunJobsClient.onJobCompleted` + `PubSubWebhookRoutes` (thread `failedGate`),
  `entrypoint-common.sh` (publish `failedGate`)
- Root `CLAUDE.md` — new standing "Failed-gate file" rule for workers

## Gotcha logged

Adding the `failed_gate` migration pushed `AgentDatabase.migrate()` past detekt's `LongMethod`
limit (30 lines). Fixed idiomatically by splitting it into per-migration helper functions
(`addWorkerMetricColumns`, `addFailureAttributionColumns`, `createJobDurationsView`) — which also
reads better. `compileTestKotlin` + `detekt` before pushing catches this class of thing locally.

## Verification

- `./gradlew :orchestrator:test` — green, incl. new `CloudLoggingClient` model-parse assertions
  (model resolved from `modelUsage`; null when `modelUsage` is empty) and `JobCompletionEventTest`
  (the `failedGate` wire contract, present + absent).
- `./gradlew :pipelineCore:build detekt` — green.
- **Coverage boundary (honest):** `JobRepository` DB persistence and the full worker→orchestrator
  gate flow aren't unit-tested here — `JobRepository` hits Postgres (no in-memory harness exists,
  consistent with the repo) and `CloudRunJobsClient` is coupled to the concrete repository. Those
  are exercised by the live pipeline E2E scenarios, not unit tests.
