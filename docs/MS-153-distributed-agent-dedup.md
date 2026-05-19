# MS-153: Distributed Agent Deduplication

## Problem

The `AgentLaunchService` used an in-memory `activeKeys` set to prevent concurrent agent spawns for the same Jira ticket. This worked within a single JVM session but failed in two observed scenarios:

1. **Container restart** — `activeKeys` is cleared on restart. A Jira webhook retry would see an empty set and spawn a second agent against a ticket already being handled (potentially in a Cloud Run job or a process that survived if the restart was partial).
2. **Rapid label edits** — Any change to an `autonomous`-labeled ticket in Jira fires a webhook. If the ticket is in Progress and a label is added mid-run, the duplicate webhook had the same `status = In Progress` and `assignee = bot`, so the route's filter passed and a second agent spawned into the same worktree directory.

## Approach Evaluated

### Options Considered

| Option | Pros | Cons |
|---|---|---|
| Ticket status pre-check | No infra, reads Jira | TOCTOU race; In Progress is the trigger, can't distinguish first vs. duplicate |
| **Jira label as lock** | No new infra, survives restarts, fast check from payload | Label must be removed on exit; small race window between webhook receipt and label add (same-session `activeKeys` closes this) |
| Persistent store (SQLite/Redis/DB table) | Authoritative | Requires new infra for local path (Cloud Run path already uses Supabase) |
| Webhook idempotency keys | Precise | Jira doesn't expose delivery IDs consistently |

### Selected: Jira `agent-active` label as the distributed lock

Appropriate for current scale (single container demo). Requires no new infrastructure beyond the Jira API already in use.

## Implementation

### Lock protocol

1. **Check** — Webhook route checks `"agent-active" !in fields.labels` in the incoming payload. If the label is present, `shouldFire = false` and the spawn is skipped with no API call.
2. **Acquire** — After a successful `activeKeys.add(key)` in `spawnAgent`, a fire-and-forget coroutine calls `JiraApiService.addAgentActiveLabel(ticketKey)` via `PUT /rest/api/3/issue/{key}` with `{"update":{"labels":[{"add":"agent-active"}]}}`.
3. **Release** — In the `finally` block of the agent-monitoring coroutine (inside `withContext(NonCancellable)` so it runs even on cancellation), `removeAgentActiveLabel(ticketKey)` is called. This runs before teardown so the key is freed atomically with the `activeKeys.remove`.

### Two-layer dedup

| Scenario | Guard |
|---|---|
| Same-session duplicate webhook (rapid label edit mid-run) | `activeKeys.add()` atomic gate |
| Cross-session duplicate (container restart + Jira webhook retry) | `agent-active` label in webhook payload |
| Cloud Run path (any scenario) | `JobRepository.shouldDispatch()` via Supabase Postgres |

### API

New interface in `JiraApiService.kt`:

```kotlin
interface JiraDeduplicator {
    suspend fun addAgentActiveLabel(ticketKey: String)
    suspend fun removeAgentActiveLabel(ticketKey: String)
}
```

Implemented in `JiraApiService` using the Jira update API's `{"update":{"labels":[{"add"|"remove":"agent-active"}]}}` payload. Failures are logged and swallowed — a failed label add degrades to same-session-only dedup (still safe within a single container session).

## Files Changed

- `agent/src/main/kotlin/com/mediasage/agent/service/JiraApiService.kt` — `JiraDeduplicator` interface + implementation
- `agent/src/main/kotlin/com/mediasage/agent/routes/JiraWebhookRoutes.kt` — `AGENT_ACTIVE_LABEL !in fields.labels` added to `shouldFire`
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — label add on spawn, remove in teardown, `jiraDeduplicator` parameter
- `agent/src/main/kotlin/com/mediasage/agent/di/AgentModule.kt` — `JiraDeduplicator` Koin singleton, injected into `AgentLaunchService`
- `agent/src/test/kotlin/com/mediasage/agent/AgentLaunchServiceTest.kt` — `FakeJiraDeduplicator`, 3 new label lifecycle tests
- `agent/src/test/kotlin/com/mediasage/agent/JiraWebhookRouteTest.kt` — `agentActiveLabelPreventsSpawn` test, `webhookPayload` updated to accept labels

## Teardown ordering note

`activeKeys.remove(key)` was moved to run **before** `teardown?.invoke()` in the `finally` block. This ensures that by the time the worktree-removal latch fires (used in tests), the key is already cleared and a re-trigger for the same ticket is immediately possible. Previously, the race between the test thread waking from `latch.await` and `activeKeys.remove` executing was narrow but observable once `withContext(NonCancellable)` introduced a suspension point.
