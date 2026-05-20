# MS-200 — Add duration tracking view to Supabase jobs table

## Problem

The `jobs` table captures `started_at` and `completed_at` timestamps but provided no derived column for wall-clock duration. Querying execution time for reporting or cost estimation required manual `EXTRACT(EPOCH FROM ...)` arithmetic at query time.

## Solution

Added a `job_durations` Postgres view that computes `duration_seconds` from existing columns. No schema migration needed — the view is a read-only projection over the existing `jobs` table.

### View DDL

```sql
CREATE OR REPLACE VIEW job_durations AS
SELECT
  job_id,
  ticket_key,
  status,
  EXTRACT(EPOCH FROM (completed_at - started_at))::int AS duration_seconds,
  created_at,
  started_at,
  completed_at
FROM jobs
WHERE started_at IS NOT NULL;
```

`duration_seconds` is `NULL` for in-progress jobs (those with `started_at` but no `completed_at` yet), so the view safely includes RUNNING jobs without crashing.

## How it's applied

`AgentDatabase.init()` runs `CREATE OR REPLACE VIEW` inside a transaction immediately after `Database.connect()`. This is idempotent — redeploying the agent updates the view definition in place without manual Supabase SQL editor steps.

```kotlin
transaction {
    exec(
        """
        CREATE OR REPLACE VIEW job_durations AS
        SELECT ...
        """.trimIndent()
    )
}
```

## Kotlin query

`JobRepository.getJobDurations()` reads the view via a raw `exec()` call (Exposed doesn't model views natively):

```kotlin
suspend fun getJobDurations(): List<JobDurationRow>
```

Returns rows ordered by `started_at DESC`. `durationSeconds` is nullable to handle RUNNING jobs.

## Example query output

```sql
SELECT ticket_key, status, duration_seconds FROM job_durations ORDER BY started_at DESC LIMIT 5;
```

| ticket_key | status    | duration_seconds |
|------------|-----------|-----------------|
| MS-199     | COMPLETED | 312             |
| MS-197     | COMPLETED | 287             |
| MS-195     | COMPLETED | 401             |
| MS-194     | COMPLETED | 268             |
| MS-200     | RUNNING   | NULL            |

`duration_seconds` for COMPLETED jobs is the wall-clock seconds from worker start to finish, useful for estimating Cloud Run cost (billed per vCPU-second).

## Key Learnings

- **`CREATE OR REPLACE VIEW` is idempotent** — safe to re-run on every server startup without guarding for existence.
- **Exposed `exec(stmt, transform)` is the escape hatch for raw SQL** when Exposed's typed DSL doesn't cover the construct (views, CTEs, EXTRACT, etc.).
- **NULL handling for in-progress rows**: `EXTRACT(EPOCH FROM (NULL - started_at))` is NULL, not an error. `rs.wasNull()` after `getInt()` is needed in Kotlin to distinguish zero-duration from absent duration.
- **No migration needed for views** — they are schema objects that sit above the table and don't affect the Room/Exposed entity definitions or the `jobs` table itself.
