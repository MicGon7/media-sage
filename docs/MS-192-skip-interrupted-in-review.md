# MS-192: Skip Re-dispatch for INTERRUPTED Jobs Where Jira Ticket Is Already In Review

## What was built

When the orchestrator restarts, `recoverInterruptedJobs()` queries for all `RUNNING` rows and attempts to resume them. If a Cloud Run execution is gone, the row is marked `INTERRUPTED`, making it retry-eligible. But if the worker had already completed its work and opened a PR before the restart, the next webhook re-dispatch creates a duplicate agent run.

This ticket adds a Jira status check at the `doDispatch()` dedup boundary: before re-dispatching an `INTERRUPTED` job, the orchestrator now checks the Jira ticket's current status and takes one of four actions:

| Jira status | Action |
|---|---|
| `In Review` or `Done` | Mark Supabase row `COMPLETED`, skip dispatch |
| `In Progress` | Re-dispatch normally |
| `To Do` | Skip dispatch (ticket was manually reset; human must move it to In Progress) |
| `null` (Jira API failure) | Fail open — re-dispatch (avoid blocking on Jira outage) |

## What changed

**`JiraApiService.kt`**
- Added `JiraTicketStatusChecker` interface with `suspend fun getTicketStatus(ticketKey: String): String?`
- `JiraApiService` implements `JiraTicketStatusChecker` via a `GET /rest/api/3/issue/{key}?fields=status` call, following the same pattern as `getTicketContent()`

**`JobRegistry.kt` / `JobRepository.kt`**
- Added `suspend fun findLatestJob(ticketKey: String): JobRow?` to the `JobRegistry` interface
- `JobRepository` implements it with the same query pattern as `shouldDispatch()`, returning the latest row with its `jobId` and `JobStatus`

**`AgentLaunchService.kt`**
- Added `jiraStatusChecker: JiraTicketStatusChecker? = null` constructor parameter
- In `doDispatch()`, after `shouldDispatch()` passes, calls `findLatestJob()` only when the checker is present — no extra HTTP calls for `RUNNING` or `COMPLETED` rows
- The `when` expression maps Jira status to dispatch/skip/complete decisions

**`AgentModule.kt`**
- Added `single<JiraTicketStatusChecker> { get<JiraApiService>() }` binding
- Passes `get<JiraTicketStatusChecker>()` to the `AgentLaunchService` constructor

## Why Jira is the right source of truth here

The orchestrator cannot determine the worker's final outcome from Cloud Run alone — if the execution record is gone, the LRO is unrecoverable. Jira ticket status is updated by the agent as its last step (transition to In Review), so it reliably reflects whether the work completed. The check only happens for `INTERRUPTED` rows, not for `RUNNING` or `COMPLETED` rows, keeping the common path free of extra HTTP calls.

## Fail-open on Jira API failure

If `getTicketStatus()` returns `null` (e.g. Jira is down), the code falls through to re-dispatch. This is intentional: a blocked re-dispatch is harder to recover from than a duplicate agent run, which the existing `shouldDispatch()` check will catch the second time around anyway.

## Test coverage

Four new tests in `JobDispatchTest.kt` cover each Jira status branch for an `INTERRUPTED` row: `In Review` (mark COMPLETED, skip), `Done` (mark COMPLETED, skip), `In Progress` (dispatch), and `To Do` (skip without DB change). The existing INTERRUPTED test without a checker confirms no Jira call is made when `jiraStatusChecker` is null.
