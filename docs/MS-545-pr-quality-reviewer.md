# MS-545: Independent Code-Quality Reviewer for Autonomous PRs

## What Changed

Every autonomous (`ticket-work`) PR now gets a second, independent review in addition to the
existing inline AC-compliance judge. The new `pr-quality-work` job clones the repository, reviews
the PR with **full repo context**, and posts a GitHub review as advisory `COMMENT` — it never
blocks merge. Scope is review + comment only; pushing fix commits is deferred to a follow-up.

### Before

```
ticket-work succeeds → opens PR
  → orchestrator runs the inline AC judge (ClaudeAgentService, diff-only, one API call)
```

The judge sees only the diff. It can confirm the change matches the ticket's acceptance criteria,
but it cannot judge whether code is idiomatic, reuses existing helpers, or — the highest-value
failure mode — whether it is a **correct implementation of a *wrong* or misapplied rule**
(e.g. MS-535 followed a CLAUDE.md rule that contradicted NowInAndroid; MS-543 faithfully refactored
an unreachable screen). Those need full repo context and a reviewer told to *question* the rules.

### After

```
ticket-work succeeds → opens PR
  → orchestrator runs the inline AC judge (unchanged)
  → AND dispatches pr-quality-work (Cloud Run Job, repo cloned)
       dedup key:     QUALITY-{prNumber}
       jiraTicketKey: the real ticket key (e.g. MS-545)
       → reviewer clones repo, reads changed files + siblings + CLAUDE.md
       → posts ONE GitHub review, state COMMENT, summary prefixed "🤖 **Agent:**"
```

Both reviews run in parallel and are advisory-only. Neither gates PR visibility or merge.

## Key Decisions

### Worker-shaped, not an inline service

Unlike the AC judge (a diff-only Anthropic API call inside the orchestrator), a quality reviewer
needs the working tree. Reusing the existing worker image, Cloud Run Job dispatch, and Pub/Sub
completion path gives us the clone "for free" — the entrypoint already runs `claude -p "/$JOB_TYPE"`.
So the new job is just a new `JOB_TYPE=pr-quality-work` + a new skill in `.claude/commands/`. No new
Cloud Run job resource: the same `media-sage-agent-worker` job handles it via a per-run `JOB_TYPE`
override, and all its static env vars (incl. `GITHUB_OWNER/REPO`) already exist.

### Distinct dedup key: `QUALITY-{n}`, never `PR-{n}`

`pr-review-work` already dedups on `PR-{prNumber}`. Reusing that key would make the two jobs collide
in `JobRegistry.shouldDispatch` — whichever dispatched first would suppress the other. `pr-quality-work`
uses `QUALITY-{prNumber}` so both can target the same PR independently. A dispatch-level test asserts
`launchForPrReview(42)` and `launchForQualityReview(42, …)` both fire.

### Two keys, two jobs: `TICKET_KEY` closes the row, `JIRA_TICKET_KEY` guards recursion

The completion event's `ticketKey` is read from the worker's `TICKET_KEY` env var and is used to find
the job's own row in Supabase (`findRunningByTicketKey`). We pass `TICKET_KEY=QUALITY-{n}` (the synthetic
dedup key) so the completion event matches the inserted row and marks it `COMPLETED` cleanly — rather
than leaving it stuck `RUNNING` until restart recovery.

Separately, `JIRA_TICKET_KEY` carries the *real* ticket key. It is stamped onto the completion event as
`jiraTicketKey`, which makes the post-completion recursion guard exclude this job:

```kotlin
// Only fires for ticket-work completions (jiraTicketKey == null). PR review, conflict,
// and quality jobs all set jiraTicketKey, so they never re-trigger the judge or another review.
if (event.jiraTicketKey == null && event.status == "success" && prNumber != null) {
    agentService.evaluate(event.ticketKey, prNumber)          // AC judge (unchanged)
    agentLauncher.launchForQualityReview(prNumber, event.ticketKey)  // quality review
}
```

`JIRA_TICKET_KEY` also lets the entrypoint post the run summary to the right Jira ticket.

### Comments posted by the agent via the reviews API — not the broken Kotlin path

The existing judge's *inline* PR comments have never actually posted (a latent `commit_id` 422 bug in
`GitHubApiClient.postInlineReviewComment`, tracked separately). `pr-quality-work` deliberately does not
reuse that Kotlin path. The skill posts one review via
`gh api POST /repos/{owner}/{repo}/pulls/{n}/reviews` with `event=COMMENT` and a `comments` array of
`{path, line, body}`. That endpoint anchors comments to the PR's latest commit automatically, sidestepping
the 422.

### Loop guard: `COMMENT` state + `🤖 **Agent:**` prefix

`pr-review-work` is triggered by human `changes_requested` reviews. Two existing guards keep the quality
review out of that path: the review is posted as the bot identity (`sender == botLogin` is ignored), and
its state is `COMMENT`, never `REQUEST_CHANGES`. The summary is also prefixed `🤖 **Agent:**`, matching
the existing prefix guard in `parseReviewContext`. So commenting cannot self-trigger another review.

### Suggestion blocks by shape of fix, not by "mechanical vs quality"

The skill emits a GitHub ` ```suggestion ` block whenever a finding's fix is a literal replacement on
lines already in the diff (one-click / batch-committable), regardless of whether the fix is "mechanical"
or a "quality" change. Findings that can't be expressed as an on-diff literal (reuse-the-right-helper,
wrong pattern, wrong/misapplied rule, reachability) stay as plain advisory comments — and those are
expected to be the majority of the high-value findings.

### Cost control: a runaway guard, not context trimming

Full repo context is the entire point, so it is intentionally not trimmed. The only lever is a bounded
review instruction in the skill (read the diff and what's needed to judge it; don't build, don't clone
extra repos). A shallow clone (`--depth 1`, already the entrypoint default) is fine — the reviewer needs
the working tree, not history.

## Files Changed

| File | Change |
|---|---|
| `service/AgentLauncher.kt` | Added `launchForQualityReview(prNumber, jiraTicketKey)` to the interface |
| `service/AgentLaunchService.kt` | Implemented it — key `QUALITY-{n}`, job type `pr-quality-work`, identifiers `PR_NUMBER` + `TICKET_KEY` + `JIRA_TICKET_KEY` |
| `routes/PubSubWebhookRoutes.kt` | `processCompletion` now also dispatches the quality review alongside the AC judge; takes an `AgentLauncher` |
| `Application.kt` | Passes `agentLaunchService` into `pubSubWebhookRoutes` |
| `.claude/commands/pr-quality-work.md` | New skill — the reviewer's instructions (challenge rules + reachability; post one `COMMENT` review) |
| `agentruntime/CLAUDE.md` | Dispatch-model job-type table + post-PR review notes |
| `test/JobDispatchTest.kt` | 4 new dispatch tests (key, identifiers, no-collision with `PR-{n}`, dedup) |
| `test/GitHubWebhookRouteTest.kt` | `FakeAgentLauncher.launchForQualityReview` override |

## Post-deploy verification

The dispatch change ships in the orchestrator image; the skill ships via the worker's runtime `git clone`
once merged to `main`. To verify end-to-end after deploy:

1. Trigger any autonomous ticket (e.g. `/pipeline-test a`) and let `ticket-work` open its PR.
2. Confirm orchestrator logs show both `running AC compliance evaluation` and `dispatching quality review`.
3. Confirm a `QUALITY-{n}` job row appears in Supabase and reaches `COMPLETED`.
4. Confirm the PR receives a GitHub review with state `COMMENT`, summary prefixed `🤖 **Agent:**`.
5. Confirm the quality review did **not** trigger `pr-review-work` (no `PR-{n}` review job appears).
