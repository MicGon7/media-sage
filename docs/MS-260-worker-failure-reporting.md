# MS-260: Worker Failure Reporting and Wall-Clock Duration

## What was built

Three fixes to the Pub/Sub completion reporting pipeline, all discovered during MS-257 e2e validation.

## Problem 1: SIGTERM reported as success

When Cloud Run kills a container for exceeding the task timeout (1800s), it sends SIGTERM to the
bash process. Without an explicit TERM trap, bash may exit with code 0 (the last successful
command's exit code) rather than the signal's conventional exit code (143 = 128 + SIGTERM). The
EXIT trap saw code 0 and published `status=success` to Pub/Sub, causing the orchestrator to mark
the job COMPLETED in Supabase — permanently blocking retries via the dedup gate.

### Fix

Added `trap 'exit 143' TERM INT` in `worker-entrypoint.sh` above the EXIT trap:

```bash
trap 'exit 143' TERM INT
trap 'publish_completion $?' EXIT
```

When bash receives SIGTERM, the TERM trap fires first and calls `exit 143`. That explicit `exit`
causes the EXIT trap to fire with `$?=143`, which is non-zero, so `publish_completion` sends
`status=failure`. The orchestrator's existing `onJobCompleted` path already calls `markFailed`
when `succeeded=false` — no orchestrator change needed.

The dedup gate (`shouldDispatch`) already treats FAILED as retry-eligible, so a re-trigger
(webhook re-fire or manual Jira transition) will dispatch a fresh job.

## Problem 2: Duration showed Claude API time, not wall-clock

The `duration_ms` field in the Cloud Logging result event is the time Claude Code spent in API
calls only. It excludes container cold start (~2–3 min), GitHub token generation (~5s), and git
clone (~10s). For the MS-260 autonomous run, Cloud Logging showed ~2m 22s while actual
wall-clock was ~33 minutes (killed by Cloud Run timeout).

### Fix

`startedAt` is already stored in Supabase when `markRunning` fires (the moment Cloud Run dispatch
succeeds). Added it to `JobRow` so `findRunningByTicketKey` returns it. In `processCompletion`,
capture `Instant.now()` as the Pub/Sub receipt time and compute:

```kotlin
val wallClockMs = job.startedAt?.let { receiptTime.toEpochMilli() - it.toEpochMilli() }
```

Pass `wallClockMs` to `onJobCompleted` → `handleSuccess` → `postConsolidatedComment`.
`postConsolidatedComment` uses `wallClockMs ?: m.durationMs` — falling back to Claude API time
only when `startedAt` is unavailable (e.g. the recovery path where no `startedAt` is read).

Wall-clock duration reflects what the developer actually waits: dispatch to completion, including
all container overhead.

## Problem 3: PR review / conflict resolution workers didn't write the Jira comment file

`PR_REVIEW_PROMPT` and `CONFLICT_RESOLUTION_PROMPT` ended with "Follow the Agent Guidelines in
CLAUDE.md." The guidelines are written for full autonomous ticket workflows and include the
`/tmp/jira_comment.txt` instruction. Workers running these prompts exited without writing the
file, so the orchestrator posted a bare metrics-only comment with no task summary or PR link.

### Fix

Added an explicit instruction to both prompts:

```
Before exiting, write a brief plain-text summary to /tmp/jira_comment.txt covering:
what was done, the PR URL (gh pr view N --json url -q .url), and quality gate results.
Use the format from CLAUDE.md Agent Guidelines (no bold markdown).
```

The instruction is explicit rather than relying on the worker to read and interpret the full
CLAUDE.md guidelines. Workers for these narrow tasks don't run the full workflow, so they need a
direct cue.

## Files changed

- `agent/worker-entrypoint.sh` — SIGTERM/INT trap
- `agent/src/main/kotlin/com/mediasage/agent/db/JobRepository.kt` — `startedAt` in `JobRow`,
  populated in `findRunningByTicketKey`
- `agent/src/main/kotlin/com/mediasage/agent/routes/PubSubWebhookRoutes.kt` — capture receipt
  time, compute `wallClockMs`, pass to `onJobCompleted`
- `agent/src/main/kotlin/com/mediasage/agent/service/CloudRunJobsClient.kt` — `wallClockMs`
  param through `onJobCompleted` → `handleSuccess` → `postConsolidatedComment`
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — `/tmp/jira_comment.txt`
  instruction in `PR_REVIEW_PROMPT` and `CONFLICT_RESOLUTION_PROMPT`

## What was not changed (MS-259)

`CONFLICT_RESOLUTION_PROMPT` still hardcodes `origin/main` as the rebase target. Fixing it to
use the PR's actual base branch (`base.ref` from the GitHub event payload) is tracked in MS-259
and requires passing the base branch through the webhook route → `launchForConflictResolution`.
That is a separate, focused change.
