# MS-180: Replace open classes used for testability with interfaces

## Summary

Replaced testability seams that relied on `open class` / `open fun` with proper Kotlin interfaces and injected fakes across the `:agent` module. The change applies the pattern introduced by `JobDispatcher` in MS-175 to `AgentLaunchService`.

## Problem

`AgentLaunchService` was `open` with `open` methods to support two distinct seams:

1. **OS operations (Template Method pattern):** `createWorktree`, `removeWorktree`, `buildAgentProcess` — overridden in `AgentLaunchServiceTest` via anonymous subclasses to avoid spawning real git processes.
2. **Behavioral dispatch:** `launchForPrReview`, `launchForCommentReview`, `postInlineCommentReply` — overridden in `GitHubWebhookRouteTest` via `TrackingAgentLaunchService : AgentLaunchService`.

Making a class `open` purely for testability is a code smell in Kotlin. It leaks implementation details, makes the class harder to reason about (subclasses may override anything), and couples tests to the concrete class rather than a contract.

## Solution

Two interfaces extracted, both with production implementations and injected test fakes:

### `AgentLauncher` interface

```kotlin
interface AgentLauncher {
    fun launch(ticketKey: String, ticketContent: String? = null, dryRun: Boolean = false): Boolean
    fun launchForPrReview(ticketKey: String, prNumber: Int, branchRef: String, commentBody: String, reviewerLogin: String): Boolean
    fun launchForCommentReview(ticketKey: String, prNumber: Int, branchRef: String, commentBody: String): Boolean
    fun postInlineCommentReply(prNumber: Int)
}
```

`AgentLaunchService` implements it. Koin registers both: `single { AgentLaunchService(...) }` and `single<AgentLauncher> { get<AgentLaunchService>() }`. Routes and tests depend only on `AgentLauncher`. `Application.kt` still resolves the concrete `AgentLaunchService` to call `recoverInterruptedJobs()`, which is not part of the interface (infrastructure concern, not dispatch contract).

### `WorktreeManager` interface

```kotlin
interface WorktreeManager {
    fun createWorktree(path: String, branchRef: String? = null): Boolean
    fun removeWorktree(path: String)
    fun buildAgentProcess(prompt: String, workDir: File): Process
}
```

`DefaultWorktreeManager(repoPath)` wraps the real OS calls and is the default constructor argument of `AgentLaunchService`:

```kotlin
class AgentLaunchService(
    private val repoPath: String,
    ...
    private val worktreeManager: WorktreeManager = DefaultWorktreeManager(repoPath)
) : AgentLauncher
```

Tests inject `FakeWorktreeManager` without subclassing the service.

## Race condition fixed

The prior autonomous run (execution `media-sage-agent-worker-7wgkw`) identified a race condition: `teardown?.invoke()` fired the latch in `removeWorktree`, but `activeKeys.remove(key)` happened *after* teardown in the same `finally` block. Tests polling the latch would proceed before the key was cleared.

**Fix:** Reorder the `finally` block so the key is released *before* teardown fires:

```kotlin
finally {
    activeKeys.remove(key)
    activeRuns.remove(key)
    teardown?.invoke()
}
```

By the time the latch fires inside `teardown`, `isActive(key)` is already `false`.

## Files changed

| File | Change |
|---|---|
| `service/AgentLauncher.kt` | New interface |
| `service/WorktreeManager.kt` | New interface + `DefaultWorktreeManager` |
| `service/AgentLaunchService.kt` | Remove `open`, implement `AgentLauncher`, accept `WorktreeManager`, fix race |
| `di/AgentModule.kt` | Add `single<AgentLauncher> { get<AgentLaunchService>() }` |
| `routes/GitHubWebhookRoutes.kt` | Inject `AgentLauncher` |
| `routes/JiraWebhookRoutes.kt` | Inject `AgentLauncher` |
| `AgentLaunchServiceTest.kt` | `FakeWorktreeManager` replaces anonymous subclasses |
| `GitHubWebhookRouteTest.kt` | `FakeAgentLauncher : AgentLauncher` replaces `TrackingAgentLaunchService : AgentLaunchService` |

## Pattern guidance

**Use interfaces for testability seams, not `open`.** The `open class` approach:
- Couples test fakes to the concrete implementation (subclass must match all constructor params)
- Allows subclasses to override anything, not just the intended seam
- Makes it unclear which methods are extension points vs. implementation details

**Interface + injected fake** is idiomatic Kotlin:
- Fakes implement only the contract they need
- The seam is explicit and documented by the interface
- No inheritance hierarchy, no superclass constructor coupling

**Two Koin registrations for one class** (`single<ConcreteType>` + `single<InterfaceType>`) is the standard pattern when you need both the concrete type (for startup hooks like `recoverInterruptedJobs`) and the interface type (for routes and tests).
