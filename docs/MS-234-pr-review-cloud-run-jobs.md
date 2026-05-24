# MS-234: Route PR Review Agents Through Cloud Run Jobs

## What Changed

All agent work now dispatches via Cloud Run Jobs. The orchestrator is a pure event router — it receives webhooks, builds prompts, and dispatches jobs. No agent processes run locally on the orchestrator container.

### Before

```
Jira webhook  → Cloud Run Job  ✓
GitHub webhook → local process  ✗  (spawnAgent → ProcessBuilder → claude CLI)
```

### After

```
Jira webhook   → Cloud Run Job  ✓
GitHub webhook → Cloud Run Job  ✓
```

## Key Decisions

### Re-request review moved into the prompt

Previously, `launchForPrReview` used a `teardown` lambda that called `requestReview()` after `process.waitFor()`. This worked because the orchestrator held a reference to the running process.

With Cloud Run, the orchestrator dispatches a job and immediately releases the reference — there's no `waitFor()`. Rather than making the Pub/Sub completion handler distinguish PR jobs from ticket jobs, the re-request review responsibility was moved into the agent prompt itself:

```
"Then push a fix commit and run: gh pr review-request {prNumber} --reviewer {reviewerLogin}"
```

The worker already uses `gh` for all GitHub operations. This keeps the orchestrator thin.

### AgentBriefing removed

`AgentBriefing` called the Claude CLI to pre-analyze a ticket before dispatch, producing a briefing of relevant files and patterns. It was behind a feature flag (`agentBriefingEnabled`) due to added latency in the dispatch hot path.

With mature ticket workflows (explicit AC, relevant files listed, detailed CLAUDE.md architectural map), the briefing's value is marginal. MS-232 completed cleanly without it. The economics also shifted: worker exploration turns are cheap Cloud Run compute; the briefing's cost is paid in dispatch latency and Anthropic API tokens.

Removed: `AgentBriefing.kt`, `agentBriefingEnabled` config flag, `verboseLogging` config flag.

### WorktreeManager removed

`WorktreeManager` managed git worktrees for local agent isolation. With all agents running in Cloud Run containers (each with a fresh repo clone), there are no shared worktrees to manage. The interface, its default implementation, and all wiring were deleted.

## Architecture After This Ticket

The orchestrator's responsibilities are now cleanly separated:

| Concern | Where it lives |
|---|---|
| Event routing | Orchestrator (Ktor routes) |
| Prompt building | Orchestrator (`AgentLaunchService`) |
| Job dispatch | Orchestrator → Cloud Run Jobs API |
| Agent execution | Cloud Run Job container |
| Repo access | Worker's fresh clone inside the container |

## What Was Deleted

- `AgentBriefing.kt` — Claude CLI subprocess for pre-dispatch context enrichment
- `WorktreeManager.kt` + `DefaultWorktreeManager` — git worktree management for local isolation
- `dispatchToLocalProcess()`, `spawnAgent()`, `pipeStreams()` — local Claude Code process lifecycle
- `requestReview()` — post-process teardown (moved to agent prompt)
- `AgentLaunchServiceTest.kt` — tested local process dedup (now obsolete)
- `AgentBriefingTest.kt` — tested AgentBriefing (now deleted)
- `verboseLogging` config — controlled pipeStreams verbosity (no stream to pipe)
- `agentBriefingEnabled` config — feature flag for AgentBriefing (now deleted)

## Smoke Test

To verify the PR review flow end-to-end:
1. Leave a review comment with **Changes requested** on an open PR for an `autonomous`-labeled ticket
2. Confirm a `PR-{prNumber}` Cloud Run Job is dispatched (orchestrator logs: `[PR-42] job inserted — dispatching to Cloud Run`)
3. Confirm the worker checks out the branch, makes the change, and pushes a fix commit
4. Confirm `gh pr review-request` is called and the original reviewer is re-requested
5. Confirm a `🤖 **Agent:**` comment is posted if no code change was needed
