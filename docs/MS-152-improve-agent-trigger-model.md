# MS-152: Improve Autonomous Agent Trigger Model, Log Levels, and Re-request Review

## What changed

Three behavioral fixes to the autonomous agent pipeline, plus MS-154 folded in.

### 1. Jira trigger model (bot assignee + In Progress)

**Old:** `autonomous` label + `To Do` status  
**New:** bot account assignee + `In Progress` transition

The Jira webhook JQL was `project = MS AND labels = autonomous`. The route checked for the `autonomous` label and `To Do` status. This meant the ticket had to be in To Do when the webhook fired, and the agent was responsible for transitioning it to In Progress itself.

The new model decouples the trigger from the label. The JQL is now `project = MS` (fire on all issues), and the route checks:

```kotlin
val shouldFire = payload.webhookEvent in relevantEvents &&
    fields.assignee?.accountId == botAccountId &&
    fields.status.name == "In Progress"
```

The `autonomous` label still exists and is still applied — it's used by the GitHub webhook handler to decide whether to fire the agent on PR review events (`jiraLabelChecker.isAutonomous`). It's a documentation and filtering tag, not the Jira webhook trigger.

The bot account ID is read from `JIRA_BOT_ACCOUNT_ID` env var and passed into `webhookRoutes(botAccountId)`. The DTO gained a `JiraAssignee` type and an `assignee: JiraAssignee?` field on `JiraIssueFields`.

**Why:** Assign + drag-to-In-Progress is the natural Kanban gesture. Label-based triggers couple workflow state to a metadata tag. The new model makes the intent explicit and removes the need for the agent to manage its own ticket status on startup.

### 2. stderr log level fix in AgentLaunchService

`pipeStreams` previously routed all stderr through `log.warning`. Claude Code outputs structured stream-json on both stdout and stderr; milestone-parseable lines on stderr were rendered as Railway errors even though they were normal progress output.

Fixed by applying the same `parseStreamJsonMilestone` check used for stdout:

```kotlin
scope.launch(Dispatchers.IO) {
    BufferedReader(InputStreamReader(process.errorStream)).forEachLine { line ->
        val milestone = parseStreamJsonMilestone(line)
        if (milestone != null) {
            milestone.lines().forEach { log.info("[$key] $it") }
        } else {
            log.warning("[$key] $line")
        }
    }
}
```

`parseStreamJsonMilestone` returns `null` on any exception, so non-JSON stderr lines (shell errors, unexpected output) remain at `log.warning`.

### 3. Re-request review after agent push (MS-154)

After the agent pushes a fix commit and posts its `🤖 **Agent:**` reply comment, the "Request changes" badge on the PR remained. GitHub requires an explicit API call to re-request review — it doesn't clear the badge automatically when new commits are pushed.

`launchForPrReview` now accepts `reviewerLogin: String` and calls `requestReview` in the teardown lambda after the agent process exits:

```kotlin
teardown = {
    if (worktreeCreated) removeWorktree(worktreePath)
    requestReview(prNumber, reviewerLogin)
}
```

`requestReview` shells out to `gh pr review-request`:

```kotlin
ProcessBuilder("gh", "pr", "review-request", prNumber.toString(), "--reviewer", reviewerLogin)
```

The reviewer login comes from `payload.sender.login` in the GitHub webhook payload — the person who submitted the review. It's threaded through `WebhookContext.reviewerLogin` → `handleGitHubEvent` → `launchForPrReview`.

Failure is caught and logged but non-fatal — the fix commit and reply comment are already posted.

## Key decisions

- **MS-154 folded into MS-152**: Both touch `AgentLaunchService`. Combining avoids a second PR for a small additive change.
- **`teardown` lambda runs after agent exits**: `requestReview` is in the teardown block, which fires in the `scope.launch` that waits for `process.waitFor()`. This guarantees re-request happens after the fix commit is pushed.
- **JQL is now `project = MS`**: The route handles all filtering. Broader JQL means no Jira webhook config change is needed when new criteria are added.

## Manual steps required after deployment

1. Update Jira webhook JQL at **media-sage.atlassian.net → Settings → System → WebHooks** from `project = MS AND labels = autonomous` to `project = MS`.
2. Ensure `JIRA_BOT_ACCOUNT_ID` is set in Railway agent service env vars (value: Jira account ID of `media-sage-bot`).
