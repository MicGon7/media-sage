# MS-137: GitHub webhook — inline comment quick reply vs. full agent on submitted review

## Background

The `:agent` module previously fired the full PR-review agent for both `pull_request_review` (submitted review) and `pull_request_review_comment` (inline comment) events. This is not idiomatic — in professional review workflows, a reviewer leaves several inline comments and then submits a single formal review. Firing the agent on every inline comment wastes resources and interrupts the reviewer mid-flow.

## What Changed

### Event routing split

`GitHubWebhookRoutes.kt` now routes the two event types differently:

| Event | Condition | Behavior |
|---|---|---|
| `pull_request_review` | `submitted` + `changes_requested` | Fire full agent via `launchForPrReview()` |
| `pull_request_review_comment` | `created` | Post quick `gh pr comment` reply, no agent |

The inner handler was extracted to `handleGitHubEvent()` to keep `githubWebhookRoutes` under detekt's 30-line `LongMethod` limit.

### Quick reply

`AgentLaunchService.postInlineCommentReply(prNumber)` runs:

```bash
gh pr comment <prNumber> --body "🤖 **Agent:** I noticed your inline comment. Please submit a formal review with **Changes requested** and I'll address all your feedback in one pass."
```

This is intentionally fire-and-forget: it runs in `scope.launch(Dispatchers.IO)` and logs a warning on failure without propagating to the webhook response.

### Testability

`AgentLaunchService` and its two public methods `launchForPrReview` and `postInlineCommentReply` are now `open` so tests can subclass with `TrackingAgentLaunchService` — a fake that increments counters instead of spawning processes. The test `inlineCommentPostsQuickReplyNotAgent` asserts `agentLaunches == 0` and `inlineReplies == 1`.

## Enterprise rationale

GitHub's own documentation and enterprise review workflows recommend responding to `pull_request_review` (submitted) events, not individual `pull_request_review_comment` events, for automation triggers. Inline comments are drafts; the submitted review is the formal signal. Acknowledging inline comments with a quick informational reply is a common pattern (e.g., Dependabot, Renovate, and GitHub Copilot all respond at the review-submission level).
