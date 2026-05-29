# Agent Pipeline — Autonomous Flow

The full Level 2 autonomous loop from Jira ticket to merged PR, including the PR review and
conflict resolution cycles. Both review response and conflict resolution are webhook handlers
in the same orchestrator — not separate levels.

```mermaid
sequenceDiagram
    actor Human
    participant Jira
    participant Orchestrator as Orchestrator<br/>(Cloud Run Service)
    participant Supabase
    participant Worker as Worker<br/>(Cloud Run Job)
    participant GitHub
    participant PubSub as Pub/Sub

    Note over Human,GitHub: Level 2 — Autonomous: ticket assigned to bot + moved to In Progress

    Human->>Jira: Assign ticket to bot,<br/>move to In Progress
    Jira->>Orchestrator: POST /webhook/jira
    Orchestrator->>Supabase: Insert job row (PENDING)
    Orchestrator->>Worker: Dispatch Cloud Run Job<br/>(PROMPT + TICKET_KEY)
    Orchestrator->>Supabase: markRunning(startedAt)

    Note over Worker: Clone repo · Generate GitHub App token
    Worker->>GitHub: git clone + checkout branch
    Worker->>Worker: Run Claude Code<br/>(implement, test, commit)
    Worker->>GitHub: git push + gh pr create
    Worker->>Worker: Write /tmp/jira_comment.txt
    Worker->>PubSub: trap EXIT → publish<br/>status=success|failure

    PubSub->>Orchestrator: POST /webhook/pubsub
    Orchestrator->>Supabase: markCompleted / markFailed
    Orchestrator->>Jira: Post metrics comment<br/>(wall-clock duration, tokens, cost)

    Human->>GitHub: Review PR
    Human->>Jira: Ticket auto-transitions to Done on merge

    Note over Human,GitHub: Review loop — part of the same autonomous flow

    alt Changes requested
        Human->>GitHub: Submit review (changes_requested)
        GitHub->>Orchestrator: POST /webhook/github<br/>(pull_request_review)
        Orchestrator->>Supabase: Insert job row PR-{n} (PENDING)
        Orchestrator->>Worker: Dispatch Cloud Run Job<br/>(PR_REVIEW_PROMPT)
        Worker->>GitHub: Fix commit + re-request review
        Worker->>PubSub: trap EXIT → publish
        PubSub->>Orchestrator: POST /webhook/pubsub
        Orchestrator->>Supabase: markCompleted
        Orchestrator->>Jira: Post metrics comment
    end

    alt Merge queue conflict
        GitHub->>Orchestrator: POST /webhook/github<br/>(dequeued: merge_conflict)
        Orchestrator->>Worker: Dispatch Cloud Run Job<br/>(CONFLICT_RESOLUTION_PROMPT)
        Worker->>GitHub: git rebase + force push +<br/>re-request review
        Worker->>PubSub: trap EXIT → publish
        PubSub->>Orchestrator: POST /webhook/pubsub
        Orchestrator->>Supabase: markCompleted
    end
```

## Key design decisions

**Producer-owned events.** The worker signals its own completion via Pub/Sub (`trap EXIT`).
The orchestrator never polls Cloud Logging to determine if work is done — logs are for
observability, not control flow.

**Dedup gate.** Before every dispatch, the orchestrator checks Supabase:
- `RUNNING` or `COMPLETED` → skip (duplicate webhook or already finished)
- `FAILED` or `INTERRUPTED` → re-dispatch (retry eligible)

**Synthetic dedup keys.** PR review and conflict resolution jobs use keys like `PR-{n}` and
`CONFLICT-{n}` to deduplicate by PR, not by ticket. `JIRA_TICKET_KEY` is passed separately
so the orchestrator can post the Jira comment to the correct issue.

**Wall-clock duration.** The Jira metrics comment shows `startedAt` (Supabase, set on dispatch)
to Pub/Sub receipt time — not Claude Code API time from Cloud Logging.
