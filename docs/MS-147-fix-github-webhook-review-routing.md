# MS-147: Fix GitHub Webhook Review Routing (MS-146 consolidated)

## What Was Fixed and Added

Two issues in the Level 4 GitHub webhook review handler, consolidated from MS-146 and MS-147.

## Bug — `review.state` Case Sensitivity

`parseReviewContext` checked `payload.review?.state == "changes_requested"` (lowercase), but GitHub sends `"CHANGES_REQUESTED"` (uppercase). The agent never fired on a Request Changes review even after the webhook content-type was corrected to `application/json`.

**Fix:** Normalize via `.lowercase()` before comparing, so casing never matters regardless of what GitHub sends.

## Root Cause Discovery — Webhook Content Type

The initial 400 Bad Request symptom turned out to be a misconfigured webhook content type (`application/x-www-form-urlencoded` instead of `application/json`). Fixing the webhook config in GitHub settings resolved the 400 — no code change needed. The case sensitivity bug was then the remaining blocker.

## Feature — Comment vs Changes-Requested Review Routing (MS-146)

Previously the agent only responded to `changes_requested` reviews. A `commented` review (e.g. a reviewer asking clarifying questions) had no handler — the agent was silent.

**Correct behavior:**
- `changes_requested` → agent checks out branch in a worktree, fixes the issues, pushes a commit
- `commented` → agent reads the branch context, posts a PR comment answering the questions, no code push
- `approved` → no action

**Changes:**
- `WebhookContext` gains `reviewState: String` so the routing logic has the state after parsing
- `parseReviewContext` now accepts both `changes_requested` and `commented` states
- `handleGitHubEvent` routes to `launchForPrReview` or `launchForCommentReview` based on state
- `AgentLaunchService.launchForCommentReview` — new method with an answer-only prompt; no worktree since no code push

## Files Changed

- `agent/src/main/kotlin/com/mediasage/agent/routes/GitHubWebhookRoutes.kt`
  - `WebhookContext` expanded with `reviewState`
  - `parseReviewContext` accepts `commented` and `changes_requested` (case-insensitive via `.lowercase()`)
  - `handleGitHubEvent` routes on `reviewState`
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt`
  - Added `PR_COMMENT_REVIEW_PROMPT` constant
  - Added `launchForCommentReview` open method
- `agent/src/test/kotlin/com/mediasage/agent/GitHubWebhookRouteTest.kt`
  - `TrackingAgentLaunchService` tracks `commentReviewLaunches`
  - Added `uppercaseChangesRequestedFiresAgent` test
  - Added `commentedReviewFiresCommentAgent` test
