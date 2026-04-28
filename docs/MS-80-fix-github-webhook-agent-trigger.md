# MS-80: Fix GitHub Webhook Agent Trigger and Git Worktree Isolation

## What Changed

Fixed two bugs in the Level 4 GitHub webhook that prevented the PR review agent from firing, and added git worktree isolation to prevent the agent from switching branches in the developer's active checkout.

## Bug 1: `sender.login` Filter Was Too Broad

**Problem:** `parseWebhookContext` rejected any webhook where `sender.login == botLogin`. Since both the developer and the bot use the same GitHub account (`MicGon7`), this blocked every human comment.

**Fix:** Removed the `sender.login` check entirely. The `🤖 **Agent:**` prefix guard already prevents the agent from re-firing on its own replies — the login check was redundant and broken.

**Key insight:** When bot and human share a GitHub account, login-based filtering is the wrong layer for loop prevention. Content-based filtering (prefix check) is the right layer.

## Bug 2: `pull_request_review_comment` Only Accepted `created`

**Problem:** `relevantEventActions` mapped `"pull_request_review_comment"` to the string `"created"`. GitHub sends `action: "edited"` when a reviewer updates an inline comment. Edited comments were silently dropped.

**Fix:** Changed the map values from `String` to `Set<String>`:

```kotlin
private val relevantEventActions = mapOf(
    "pull_request_review" to setOf("submitted"),
    "pull_request_review_comment" to setOf("created", "edited")
)
```

The `takeIf` guard changed from `payload.action == expectedAction` to `payload.action in allowedActions`.

## Git Worktree Isolation

**Problem:** The agent ran in `repoPath` (the developer's main checkout) and could run `git checkout` or other branch-switching commands, clobbering the developer's active branch mid-session.

**Fix:** `launchForPrReview` now:
1. Creates a git worktree at `/tmp/media-sage-pr-{prNumber}` pointing to `branchRef`
2. Runs the Claude agent with that directory as its working dir
3. Removes the worktree (`git worktree remove --force`) in the `finally` block after the agent exits

```kotlin
fun launchForPrReview(ticketKey: String, prNumber: Int, branchRef: String, commentBody: String): Boolean {
    val worktreePath = "/tmp/media-sage-pr-$prNumber"
    return spawnAgent(key, prompt,
        setupWorkDir = {
            ProcessBuilder("git", "worktree", "add", worktreePath, branchRef)...
            File(worktreePath)
        },
        teardown = {
            ProcessBuilder("git", "worktree", "remove", "--force", worktreePath)...
        }
    )
}
```

The `spawnAgent` helper was refactored to accept optional `setupWorkDir` and `teardown` lambdas so the Jira ticket flow (`launch`) stays unchanged.

## Other Cleanup

- Removed `botLogin` parameter from `githubWebhookRoutes` and `Application.configureRouting` — it was only used for the sender check that is now gone
- Added `branchRef` to `WebhookContext` so the worktree knows which branch to check out
- Added a key `INFO` log line when a webhook context matches: `"GitHub webhook matched: ticketKey=... PR#..."`
- Removed verbose debug `also { log... }` chains that were in the WIP stash

## Test Updates

- Removed `botSenderReturns200WithoutFiring` — the sender.login filter no longer exists; a bot comment without the `🤖 **Agent:**` prefix would now (correctly) trigger the agent
- Added `reviewCommentEditedReturns200` — verifies the `edited` action now fires
- Updated `githubWebhookRoutes(TEST_SECRET)` call (removed `BOT_LOGIN` argument)
