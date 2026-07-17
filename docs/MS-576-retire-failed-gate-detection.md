# MS-576 — Retire all failed-gate detection across the pipeline

## What & why

Removed the entire "which quality gate failed" machinery: the `jobs.failed_gate` column, the
`DatabasePatternDetector` + `PatternDetector` + `DetectedPattern` classes, the Slack gate line and
gate-failure trend line, the advisor tools' `failed_gate` output, the worker's
`/tmp/failed_gate.txt` handoff, and the `/pattern-sweep` skill (MS-550).

The feature was built (MS-386 → MS-388 → MS-389 → MS-550) on a pre-hardening assumption that gate
failures would be frequent and worth trending. They aren't. The key distinction the machinery
missed:

- **`jobs.status = FAILED` means the *run* died** — harness crash, judge error, infra (e.g. a CLI
  JSON-parse bug that failed a run whose work had actually succeeded).
- **A *gate* failure** (`tests` / `detekt` / `compile`) is a different thing entirely.

The pipeline **suppresses gate failures by design**: the worker reads `detekt.yml` before writing
code, writes targeted tests until they pass, and CI is the authoritative gate. So a worker only
writes `failed_gate` when it *gives up* on a gate it can't self-resolve — which has happened
**~never**: 0 of 17 FAILED rows carried a `failed_gate` (checked 2026-07-17 via the advisor
`query_runs` tool). The machinery got us nothing and actively conflated run-death with gate-failure.

## What was kept (deliberately)

- **`model_version`** — MS-386 bundled it into the same `ALTER TABLE`, `markFailed` signature, and
  `JobCompletionEvent`, but it is unrelated model tracking. Every edit removed only the `failed_gate`
  half.
- **Run-failure handling** — marking jobs FAILED, retry eligibility (FAILED/INTERRUPTED re-dispatch),
  and startup recovery are untouched. This ticket changes *attribution*, not *behavior*.
- **`reviewSignal`** — the review-job comment count on the completion event/notifier is a parallel
  but separate signal; kept.

## The DB migration — the one prod-touching step

`AgentDatabase.migrate()` now:
- keeps `ADD COLUMN IF NOT EXISTS model_version` (renamed helper `addModelVersionColumn`), and
- adds an idempotent `dropFailedGateColumn()` → `ALTER TABLE jobs DROP COLUMN IF EXISTS failed_gate`.

The `migrate()` block runs on every orchestrator startup, so the deployed Supabase `jobs` table
loses the column on the next deploy. `DROP COLUMN IF EXISTS` is idempotent and a no-op on a fresh DB
(where the column was never added). **This is why the ticket is assisted, not autonomous** — a human
runs and verifies the live column drop.

Safe to drop only because every reader was removed first: `JobsTable.failedGate` (the Exposed
mapping), the advisor tools, and the notifier no longer reference the column.

## Files touched

- **pipelineCore:** `JobsTable` (drop column), `JobCompletionEvent` (drop field),
  `JobRegistry`/`JobRepository` (`markFailed` loses the `failedGate` param, keeps `modelVersion`).
- **agentruntime:** deleted `feedback/detector/` (3 files); `AgentModule` unwired `PatternDetector`;
  `JobCompletionNotifier` lost the gate line, trend line, detector param, and `detectPatterns()` call;
  `CloudRunJobsClient.onJobCompleted` + `PubSubWebhookRoutes` dropped the `failedGate` arg;
  `AgentDatabase` migration change above.
- **advisor:** `QueryRunsTool`, `CompareRunsTool`, `ExplainFailureTool` no longer surface a gate
  column/field. `explain_failure` derived the gate from the transcript anyway, so no capability lost.
- **worker:** `entrypoint-common.sh` dropped the `/tmp/failed_gate.txt` read/forward block (kept the
  parallel `review_comment_count.txt` block).
- **docs/skill:** deleted `.claude/commands/pattern-sweep.md` and `docs/MS-550-pattern-sweep-skill.md`;
  scrubbed + tombstoned `MS-386`, `MS-389`, `MS-570`; removed the "Failed-gate file" rule from root
  `CLAUDE.md` and the `failed_gate` line from `agentruntime/CLAUDE.md`.
- **tests:** updated `JobCompletionEventTest`, `JobCompletionNotifierTest`, `JobDispatchTest`,
  `QueryRunsToolTest`, `CompareRunsToolTest`.

## Verification

- `./gradlew :pipelineCore:compileKotlin :agentruntime:compileKotlin :advisor:compileKotlin` — green.
- `./gradlew :pipelineCore:test :agentruntime:test :advisor:test` — green.
- `./gradlew :pipelineCore:detekt :agentruntime:detekt :advisor:detekt` — green.
- **Live DB drop** verified post-deploy (see the PR's Post-deploy verification section).
