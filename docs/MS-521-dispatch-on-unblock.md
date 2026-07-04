# MS-521: Dispatch-on-Unblock When a Blocking PR Merges

## What Changed

When a PR merges, the orchestrator now automatically dispatches any bot-assigned tickets
that were blocked by that ticket and are fully unblocked as a result. Previously, a human
had to manually move each dependent ticket to In Progress after the blocker merged.

### Before

```
PR for MS-520 merges
  → human notices MS-521 is now unblocked
  → human moves MS-521 to In Progress
  → Jira webhook fires → orchestrator dispatches MS-521 worker
```

### After

```
PR for MS-520 merges
  → GitHub webhook fires (pull_request / closed / merged)
  → orchestrator extracts MS-520 from branch ref
  → JiraTicketClient.getNewlyUnblockedTickets("MS-520") returns ["MS-521"]
  → orchestrator dispatches MS-521 worker automatically
  → Jira comment posted: "🤖 Dispatched automatically after MS-520 was merged."
```

## Key Decisions

### TicketSystemClient interface isolates Jira logic from dispatch logic

The GitHub webhook route is not allowed to know that Jira uses "Blocks" as a link type name,
that "Done" is the terminal status string, or that the REST path is `/issue/{key}?fields=issuelinks,assignee,status`.
All of that lives in `JiraTicketClient`. The route calls only two methods:

```kotlin
interface TicketSystemClient {
    suspend fun getNewlyUnblockedTickets(mergedTicketKey: String): List<String>
    suspend fun isResolved(ticketKey: String): Boolean
}
```

This keeps the interface stable if the ticket system ever changes (e.g. Linear instead of Jira),
and makes the route testable with a one-liner fake.

### Link direction: outward Blocks links from the merged ticket

Jira "Blocks" links have a direction: the ticket that blocks has an *outward* link of type "blocks";
the ticket that is blocked has an *inward* link of type "is blocked by". To find tickets that the
merged ticket was blocking, `JiraTicketClient` iterates the outward links of the merged ticket.

For each candidate, it checks two conditions before adding it to the result:
1. The assignee matches `JIRA_BOT_ACCOUNT_ID` — only bot-assigned work gets auto-dispatched.
2. All of the candidate's own inward Blocks links have status "Done" — if another blocker
   is still open, the ticket is not fully unblocked.

### Re-entrancy protection via the existing activeKeys gate

Dispatching a worker causes the worker to transition the ticket to In Progress. That transition
fires a Jira webhook back to the orchestrator. Without protection this creates a second Cloud Run
dispatch for the same ticket.

The existing `activeKeys` set in `AgentLaunchService` already solves this. `Set.add()` is a single
atomic operation: the first caller succeeds and holds the key; any concurrent caller (including the
Jira re-entrant webhook) gets `false` immediately. The re-entrant Jira webhook reaches `launch()`
after `launchForUnblockedTicket()` has already inserted the key, so it returns false without
reaching `shouldDispatch()` or Cloud Run.

### Jira comment is posted inside the dedup-guarded coroutine

The "dispatched automatically after X was merged" comment on the unblocked ticket is posted
inside `doDispatch()`, after both the `shouldDispatch()` and `shouldSkipInterrupted()` checks pass,
and before the job row is inserted. This ensures the comment is posted exactly once, only when
dispatch actually proceeds.

### JiraTicketClient extends JiraApiClient

Rather than duplicating HTTP client wiring, `JiraTicketClient` subclasses `JiraApiClient`
and accesses `httpClient`, `authHeader`, and `baseUrl` — which changed from `private` to
`protected` for this purpose. The Koin module binds the single `JiraTicketClient` instance
to both `JiraApiClient` and `TicketSystemClient` interfaces, so `AgentLaunchService`
continues to receive its `JiraApiClient` reference unchanged.

### Ticket key is extracted from the branch ref, not the PR title

Branch refs follow the project convention: `feature/MS-NNN-description`. The regex `MS-\d+`
is applied to `pullRequest.head.ref`. PRs with branches that don't match (renovate updates,
hotfixes without a ticket) are silently ignored with a log line.

## Files Changed

| File | Change |
|---|---|
| `service/TicketSystemClient.kt` | New interface |
| `service/JiraTicketClient.kt` | New class — Jira implementation of `TicketSystemClient` |
| `service/JiraApiClient.kt` | `private` → `protected` for httpClient, authHeader, baseUrl |
| `service/AgentLauncher.kt` | Added `launchForUnblockedTicket(ticketKey, blockerKey)` to interface |
| `service/AgentLaunchService.kt` | Implemented `launchForUnblockedTicket`; added `blockerKey` to `DispatchOptions` |
| `routes/GitHubWebhookRoutes.kt` | `pull_request` handler calls both `handleDequeueEvent` and `handleMergeEvent` |
| `di/AgentModule.kt` | Binds `JiraTicketClient` to both `JiraApiClient` and `TicketSystemClient` |
| `test/GitHubWebhookRouteTest.kt` | 4 new route-level tests; `FakeTicketSystemClient`; `FakeAgentLauncher.launchForUnblockedTicket` |
| `test/JobDispatchTest.kt` | 5 new dispatch-level tests for `launchForUnblockedTicket` including re-entrancy |

## Smoke Test

To verify end-to-end with the deployed orchestrator:

1. Create two tickets: MS-A (blocker) and MS-B (blocked). Link MS-A → MS-B using "Blocks".
   Assign MS-B to the bot account. Leave MS-A unresolved.
2. Merge a PR whose branch contains `MS-A` in the ref (e.g. `feature/MS-A-blocker`).
3. Confirm in orchestrator logs: `PR merged (MS-A) — 1 ticket(s) unblocked`
4. Confirm the Jira comment appears on MS-B: "🤖 Dispatched automatically after MS-A was merged."
5. Confirm a Cloud Run Job is dispatched for MS-B.
