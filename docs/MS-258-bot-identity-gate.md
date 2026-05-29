# MS-258: Replace autonomous label gate with bot identity check

## What Changed

`GitHubWebhookRoutes` previously gated all webhook dispatch on a live Jira API call —
`jiraLabelChecker.isAutonomous(ticketKey)` — to check whether the ticket had the `autonomous`
label before responding to a PR conflict or review event. That gate was replaced with a single
field check on the webhook payload itself: `pullRequest.user.login == botLogin`.

## Why the Label Gate Was Wrong

Three compounding problems made `isAutonomous` the wrong design:

**1. Silent failure on accidental removal.** If the `autonomous` label was removed from a ticket —
by hand, by bulk edit, by Jira automation — the orchestrator would stop responding to that PR with
no error, no log entry, and no observable signal. The pipeline would go dark silently.

**2. Not portable across projects.** Every Jira project the pipeline targets (a future `PIPE`
sandbox project, any client project) would need to adopt the `autonomous` labeling convention.
That's external friction imposed on every new target before the first job can run.

**3. Unnecessary cross-system dependency in the hot path.** Every GitHub webhook event triggered
an outbound Jira API call whose sole purpose was to check a label that should have been derivable
from context already in the request.

## The Fix: Bot Identity

The correct gate was already in the webhook payload all along: `pull_request.user.login` is the
GitHub login of the PR author. Bot-opened PRs have `"media-sage-worker[bot]"` as the author.
Human PRs don't.

```kotlin
// parseDequeueContext — returns null if not bot-authored
if (payload.pullRequest.user.login != botLogin) return null

// parseReviewContext — same check, same result
if (payload.pullRequest.user.login != botLogin) return null
```

`botLogin` is sourced from `GITHUB_BOT_LOGIN` (already a Cloud Run env var), threaded through
`AgentConfig.githubBotLogin` → `githubWebhookRoutes(webhookSecret, botLogin)`.

## What Was Deleted

`JiraLabelChecker` was the only interface on `JiraApiService` that touched the GitHub webhook
path. With the route no longer injecting it, the entire interface became dead code:

- `JiraLabelChecker` interface + `isAutonomous()` declaration
- `JiraApiService.isAutonomous()` implementation
- `JiraIssueLabelsResponse` / `JiraLabelsFields` private DTOs (only used by `isAutonomous`)
- `single<JiraLabelChecker>` Koin binding in `AgentModule`
- `FakeJiraLabelChecker` test double + `jiraAutonomous` param in `GitHubWebhookRouteTest`

## How the Bug Was Found

This was discovered while designing the MS-257 end-to-end pipelineScenarios tests. Tracing the
real webhook flow for `e2eConflictResolution` revealed that the test would need a `JiraFixtureClient`
to create a real Jira ticket with the `autonomous` label and assign it to the bot — just to satisfy
the gate. That friction surfaced the architectural question: "why does the GitHub webhook route need
to know about Jira labels at all?"

This is the pattern pipelineScenarios tests are meant to produce — not just coverage, but
structural insights about the code that only appear when you try to describe the full real-world
scenario in one test.

## Files Changed

- `agent/routes/GitHubWebhookRoutes.kt` — `user: GitHubUser` added to `GitHubPullRequest` DTO;
  `JiraLabelChecker` injection removed; `botLogin: String` param added; both parse functions check
  PR author identity and return null for human-authored PRs
- `agent/di/AgentConfig.kt` — `githubBotLogin: String` property added
- `agent/Application.kt` — reads `app.github.botLogin`, passes to `githubWebhookRoutes()`
- `agent/di/AgentModule.kt` — `single<JiraLabelChecker>` binding removed
- `agent/service/JiraApiService.kt` — `JiraLabelChecker` interface, `isAutonomous` impl, and
  labels DTOs deleted
- `agent/test/GitHubWebhookRouteTest.kt` — `FakeJiraLabelChecker` removed; `jiraAutonomous` param
  replaced with `prAuthorLogin` in payload builders; `nonAutonomousTicket*` tests renamed to
  `humanAuthoredPr*`
