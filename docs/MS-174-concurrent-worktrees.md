# MS-174: Git Worktree Isolation for Concurrent Autonomous Agents

## Problem

Before this change, all Claude Code agent processes ran inside the single `AGENT_REPO_PATH`
working directory. Two simultaneous Jira webhooks for different tickets would spawn two
processes sharing the same git working tree — they could overwrite each other's branch
checkouts, corrupt staged changes, or fail mid-run when the other agent switched branches.

There was also no protection against duplicate webhooks. If the same ticket fired twice
(re-trigger, accidental double-send), a second agent would start on top of the first,
wasting tokens and leaving the branch in an unknown state.

## Solution

Two changes to `AgentLaunchService`:

### 1. Per-ticket active run registry (dedup gate + job store)

```kotlin
// Atomic gate — Set.add() is thread-safe and returns false in one operation if key exists.
private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

// Job registry — written only after activeKeys.add() succeeds (no race condition).
private val activeRuns = ConcurrentHashMap<String, Job>()
```

In `spawnAgent`:
```kotlin
if (!activeKeys.add(key)) {
    log.info("[$key] already in flight — ignoring duplicate webhook")
    return false
}
```

Both maps are cleaned up in a `finally` block so they always release the key, whether
the agent exits cleanly, throws, or the container restarts mid-run.

#### Why not `ConcurrentHashMap.containsKey()` + `put()`?

That pattern has a TOCTOU (time-of-check-time-of-use) race condition:

```
Thread A: containsKey("MS-99") → false
Thread B: containsKey("MS-99") → false   ← both pass before either stores
Thread A: put("MS-99", job)
Thread B: put("MS-99", job)              ← two agents running for the same ticket
```

`ConcurrentHashMap.newKeySet().add()` is atomic — it checks and sets in a single
lock-protected operation. Only one thread gets `true`. This is the idiomatic
Java/Kotlin solution for this pattern.

### 2. Git worktree isolation

```kotlin
val worktreePath = "${repoPath}-worktrees/$ticketKey"
val worktreeCreated = createWorktree(worktreePath)   // --no-checkout
return spawnAgent(
    key = ticketKey,
    workDir = if (worktreeCreated) File(worktreePath) else File(repoPath),
    teardown = { if (worktreeCreated) removeWorktree(worktreePath) }
)
```

Each ticket gets its own working directory backed by the same repo object on disk:

```
/home/agent/media-sage               ← main clone (AGENT_REPO_PATH)
/home/agent/media-sage-worktrees/
    MS-99/                           ← Agent A works here
    MS-100/                          ← Agent B works here (fully isolated)
```

No second clone, no extra disk space for the full history — git worktrees share the
`.git` object store. Each worktree has its own index and HEAD.

#### Why `--no-checkout`?

The Jira ticket flow doesn't have an existing branch to check out — the agent creates
its own branch as its first action (`git checkout -b feature/MS-XXX-...`). Checking out
`main` or `HEAD` at worktree creation time would be wasted work and could cause a
conflict if that branch is already checked out in the main clone.

Compare to the PR review flow (`launchForPrReview`), which passes `branchRef` explicitly
because it needs to check out the PR's existing branch:

```kotlin
// PR review — check out existing branch
createWorktree(worktreePath, branchRef = "feature/MS-99-some-feature")

// Jira ticket — no-checkout, agent creates its own branch
createWorktree(worktreePath)   // branchRef = null → --no-checkout
```

#### Cleanup

Worktree removal runs in a `finally` block inside the monitoring coroutine:

```kotlin
val job = scope.launch(Dispatchers.IO) {
    try {
        process.waitFor()
    } finally {
        teardown?.invoke()   // removes worktree
        activeKeys.remove(key)
        activeRuns.remove(key)
    }
}
```

`finally` guarantees cleanup even if the agent process exits non-zero, throws, or the
coroutine is cancelled. `git worktree remove --force` handles the case where the agent
left uncommitted changes.

If worktree creation fails (e.g. git not on PATH, disk full), `createWorktree` returns
`false` and the agent falls back to running in `repoPath`. This is a degraded mode — the
agent still runs, but without isolation. The failure is logged as a warning.

## Template Method pattern for testability

`buildAgentProcess` is `protected open` so tests can substitute a controllable fake
process without needing a real `claude` binary:

```kotlin
// Production
protected open fun buildAgentProcess(prompt: String, workDir: File): Process =
    ProcessBuilder(claudeCommand(prompt)).directory(workDir)...start()

// Test: blocks on stdin — agent stays "in flight" for concurrency assertions
override fun buildAgentProcess(prompt: String, workDir: File): Process =
    ProcessBuilder("cat").start()

// Test: exits immediately — verifies teardown/cleanup behaviour
override fun buildAgentProcess(prompt: String, workDir: File): Process =
    ProcessBuilder("sh", "-c", "exit 0").start()
```

Similarly, `createWorktree` and `removeWorktree` are `protected open` so tests can
capture call arguments without running real git commands.

## What does NOT change

- `AGENT_REPO_PATH` env var — still required, still points to the main clone
- The bootstrap prompt — unchanged, agent still creates its own branch
- Jira and GitHub webhook routing — unchanged
- The PR review worktree path (`/tmp/media-sage-pr-{prNumber}`) — unchanged

## Small vs large scale

| Scale | Isolation mechanism |
|---|---|
| Single container (current) | Git worktrees — one process per ticket, shared repo object |
| Multi-container / Cloud Run (MS-175) | One container per ticket — worktrees become optional |
| Worker + queue split (MS-179) | Worker owns its clone lifecycle; worktree pattern can apply within a single worker instance handling multiple concurrent tickets |

The worktree pattern is reusable at any scale where multiple agents share a repo on disk.
The dedup registry (`activeKeys`) is valuable at every scale.
