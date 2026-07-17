# MS-386 — `model_version` on the jobs table

> **Retirement note (MS-576).** This ticket originally added **two** columns: `failed_gate`
> (failure attribution) and `model_version` (model tracking). The `failed_gate` half — and
> everything built on it (the `DatabasePatternDetector`, the Slack gate-failure trend line
> (MS-388), the MS-389 auto-PR detector, and the MS-550 `/pattern-sweep` skill) — was **retired
> in MS-576** and the column dropped from the `jobs` table.
>
> **Why it was retired:** `jobs.status = FAILED` means the *run* died (harness crash, judge
> error, infra — e.g. a CLI JSON-parse bug that failed a run whose work actually succeeded). That
> is **not** the same as a *gate* failure (`tests` / `detekt` / `compile`). The pipeline suppresses
> gate failures by design — the worker reads `detekt.yml` before starting, writes targeted tests
> until they pass, and CI is the real gate — so a worker only writes `failed_gate` when it gives up
> on a gate it cannot self-resolve, which has happened **~never**: 0 of 17 FAILED rows carried a
> `failed_gate` (checked 2026-07-17). The attribution machinery was built on a pre-hardening
> assumption that gate failures would be frequent; they aren't, so it got us nothing and actively
> conflated run-death with gate-failure. `model_version` was bundled into the same migration and
> completion event but is unrelated model tracking, so it survives. The rest of this doc describes
> only the surviving `model_version` half.

## What & why

Enriches the Supabase `jobs` table so the Analyst (MS-380) can answer:

- **Which model ran the job?** (`model_version` — e.g. `claude-sonnet-4-5-20250929`)

The column is **nullable** — pre-MS-386 rows and runs where the value is unavailable degrade
gracefully (same convention as the MS-210 metric columns).

## How `model_version` is produced — it rides the existing metrics path (no new mechanism)

The orchestrator already pulls the Claude Code `result` event into `WorkerMetrics` (MS-210). That
event reports usage **per model** under `modelUsage`, keyed by model name. A worker session runs a
single model, so the first key *is* the model that ran. So `model_version` is just one more field on
`WorkerMetrics`, extracted from `modelUsage` and persisted in `markCompleted` — grouped with its
siblings (tokens, cost, turns). Captured best-effort on the failure path too, so it is recorded per
run, not only on success.

## Schema migration

`model_version` is added via the idempotent `AgentDatabase.migrate()` block
(`ALTER TABLE jobs ADD COLUMN IF NOT EXISTS model_version TEXT`), which runs on every orchestrator
startup — exactly how MS-210 added its columns. No manual Supabase SQL. (MS-576 added a sibling
idempotent `DROP COLUMN IF EXISTS failed_gate` to the same block.)

## Files touched

- `:pipelineCore` — `JobsTable` (`model_version` column), `WorkerMetrics` (`modelVersion`),
  `JobRepository` (`markCompleted` / `markFailed` persist model)
- `:orchestrator` — `AgentDatabase.migrate()` (ALTER), model parsed from `modelUsage` alongside
  the other metrics

## Gotcha logged

Adding this migration pushed `AgentDatabase.migrate()` past detekt's `LongMethod` limit (30 lines).
Fixed idiomatically by splitting it into per-migration helper functions — which also reads better.
`compileTestKotlin` + `detekt` before pushing catches this class of thing locally.

## Verification

- `./gradlew :orchestrator:test` — green, incl. model-parse assertions (model resolved from
  `modelUsage`; null when `modelUsage` is empty).
- `./gradlew :pipelineCore:build detekt` — green.
- **Coverage boundary (honest):** `JobRepository` DB persistence isn't unit-tested here — it hits
  Postgres (no in-memory harness exists, consistent with the repo). It is exercised by the live
  pipeline E2E scenarios, not unit tests.
