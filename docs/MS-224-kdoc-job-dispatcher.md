# MS-224: Add KDoc to JobDispatcher interface

## What changed

Added a KDoc comment to `agent/.../service/JobDispatcher.kt` describing the interface's role as the execution-backend abstraction (`AgentLaunchService` depends on it so Cloud Run and in-process backends are swappable via Koin).

## Notes

No logic changed. Detekt passed locally; full test suite delegated to CI per the container test policy.
