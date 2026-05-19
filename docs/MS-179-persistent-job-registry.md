# MS-179: Persistent Job Registry

## What was built

A Supabase Postgres-backed job registry for the `:agent` module, replacing the in-memory `activeKeys` dedup gate for Cloud Run worker dispatches.

**Key files:**
- `agent/src/main/kotlin/com/mediasage/agent/db/JobsTable.kt` — Exposed table schema
- `agent/src/main/kotlin/com/mediasage/agent/db/AgentDatabase.kt` — DB initializer
- `agent/src/main/kotlin/com/mediasage/agent/db/JobRepository.kt` — Dedup + status mutations
- `agent/src/main/kotlin/com/mediasage/agent/service/CloudRunDispatch.kt` — Pairs `JobDispatcher` + `JobRepository` as non-nullable
- `agent/src/main/kotlin/com/mediasage/agent/service/CloudRunJobsClient.kt` — Writes row lifecycle; overrides `recoverJob`
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — Uses `CloudRunDispatch`; adds `recoverInterruptedJobs()`

## Why persistence matters

The previous in-memory `activeKeys` set had two gaps:

1. **Restart gap**: If the orchestrator restarts (Railway redeploy, crash) while a worker is running, `activeKeys` is empty. A duplicate webhook fires and a second worker launches for the same ticket.
2. **History gap**: `activeKeys` only blocks concurrent duplicates — it has no memory of completed runs. A webhook fired hours after completion would launch the agent again.

The `jobs` table solves both: `shouldDispatch` checks the latest row status for the ticket key. RUNNING and COMPLETED both block dispatch; FAILED and INTERRUPTED allow retry.

## Job status state machine

```
PENDING → RUNNING → COMPLETED
                 ↘ FAILED
                 ↘ INTERRUPTED  (recovery path)
```

- **PENDING**: Row inserted before Cloud Run dispatch call
- **RUNNING**: Dispatch succeeded; `executionName` (LRO path) saved for recovery
- **COMPLETED / FAILED**: LRO poll resolves
- **INTERRUPTED**: Orchestrator restarted mid-poll; execution no longer found on recovery

## Why LRO polling is still necessary

Persistence stores job state, but it doesn't observe when a Cloud Run execution finishes. Without polling, rows would stay RUNNING forever after the worker exits. The LRO poll loop is what transitions rows to COMPLETED or FAILED.

## CloudRunDispatch — eliminating force-unwrap

The previous design passed `dispatcher: JobDispatcher?` and `jobRepository: JobRepository?` as separate nullable params to `AgentLaunchService`. This required `!!` (force-unwrap) at the call site, which crashes at runtime if either is null.

The `CloudRunDispatch` data class pairs them as non-nullable fields:

```kotlin
data class CloudRunDispatch(val dispatcher: JobDispatcher, val jobs: JobRepository)
```

`AgentLaunchService` takes `cloudRun: CloudRunDispatch?`. When it's non-null, both halves are guaranteed present — no force-unwrap needed.

## INTERRUPTED recovery on startup

`AgentLaunchService.recoverInterruptedJobs()` is called when the orchestrator starts:

1. Queries `jobs` table for all RUNNING rows
2. For each row with an `executionName`, calls `dispatcher.recoverJob()`
3. For each row without an `executionName` (dispatched but LRO name never saved), marks INTERRUPTED

`CloudRunJobsClient.recoverJob()` does a single GET on the saved LRO URL:
- **404 / error**: Execution is gone → mark INTERRUPTED
- **done: true**: Execution already finished → `handleDone` writes COMPLETED or FAILED
- **done: false**: Execution still running → resume `pollUntilDone`

## New environment variable

| Variable | Value |
|---|---|
| `SUPABASE_DB_URL` | `postgresql://postgres.<project-ref>:<password>@...supabase.com:5432/postgres` |

Required for Cloud Run mode. If blank, `buildCloudRunDispatch` returns null and the service falls back to local process mode.

## Supabase table setup

```sql
CREATE TABLE jobs (
  job_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_key    TEXT NOT NULL,
  prompt        TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'PENDING',
  execution_name TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at    TIMESTAMPTZ,
  completed_at  TIMESTAMPTZ
);
```

No RLS — this table is internal to the agent orchestrator, not user-facing data.

## What this unlocks

- **Persistent dedup**: duplicate webhooks are blocked even across orchestrator restarts
- **Permanent dedup**: completed tickets are never re-dispatched
- **Crash recovery**: RUNNING jobs from a crashed orchestrator are detected and re-attached on the next startup
- **Audit trail**: every job dispatch is logged with status and timestamps
