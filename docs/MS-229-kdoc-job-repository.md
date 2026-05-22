# MS-229: Add KDoc to JobRepository public methods

## What was done

Added KDoc comments to all public methods in `JobRepository.kt`, matching the style already established in `JobRegistry`, `JobDispatcher`, and `CloudLoggingClient`.

## Style notes

- Class-level KDoc explains the Postgres backing and the `Dispatchers.IO` contract (Exposed transactions are blocking, so `withContext` is required).
- Single-line `/** ... */` for simple query methods (`findLatestJob`, `markFailed`, `findRunningByTicketKey`).
- Multi-line KDoc for methods with non-obvious behaviour: `shouldDispatch` documents the dedup policy (skip RUNNING/COMPLETED, retry FAILED/INTERRUPTED); `markCompleted` documents the optional metrics persistence path; `markInterrupted` names the startup-recovery caller.
- `getJobDurations` (non-interface method) documents the `job_durations` Postgres view and the null-duration semantics for still-RUNNING rows.
