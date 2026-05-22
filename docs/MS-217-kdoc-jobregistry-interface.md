# MS-217: Add KDoc to JobRegistry Interface

## What changed

Added KDoc to `agent/src/main/kotlin/com/mediasage/agent/db/JobRegistry.kt`:

- Interface-level comment documents the role (persistent job registry backed by Supabase Postgres) and the job state machine (`PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`).
- `shouldDispatch` — explains the dedup logic: skips RUNNING (concurrent duplicate) and COMPLETED (permanent dedup); allows retry for FAILED and INTERRUPTED.
- `findLatestJob` — straightforward: most recent row or null.
- `insert` — documents the PENDING status and the returned UUID.
- `markRunning` — documents the executionName and start timestamp recording.
- `markCompleted` — documents optional `WorkerMetrics` persistence from the Claude Code result event.
- `markFailed` / `markInterrupted` — documents the completion timestamp and the crash-recovery context for INTERRUPTED.
- `findRunningJobs` — documents the startup recovery use case.

## Notes

The detekt `LongMethod` violation in `CloudRunJobsClient.kt:164` was pre-existing on `main` and unrelated to this change. CI is the authoritative gate.
