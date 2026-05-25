# MS-244: Add KDoc to AgentModule

## What was done

Added a KDoc comment to the `agentModule()` function in
`agent/src/main/kotlin/com/mediasage/agent/di/AgentModule.kt`.

The comment documents:
- The function's purpose (Koin module for the orchestration server)
- Every binding registered and which interface each concrete type satisfies
- The `config` parameter (runtime configuration from env vars, referencing `AgentConfig`)
- The `scope` parameter (coroutine scope used by `AgentLaunchService` for startup recovery)

Private helpers (`buildHttpClient`, `buildCloudRunDispatch`) were left without KDoc — they are
implementation details not visible to callers.

## Pattern learned

KDoc on a Koin module function is most useful when it enumerates the bindings rather than
restating what the code already makes obvious. Listing what interfaces each type satisfies (e.g.
`JiraApiService` → `JiraLabelChecker`, `JiraTicketFetcher`, `JiraTicketStatusChecker`) helps
readers understand the DI graph without having to trace every `single<Interface>` line.

## Files changed

- `agent/src/main/kotlin/com/mediasage/agent/di/AgentModule.kt` — added KDoc to `agentModule`
