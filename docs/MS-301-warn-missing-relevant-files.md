# MS-301: Warn at dispatch when autonomous ticket is missing a Relevant files section

## What was built

A lightweight guard in `AgentLaunchService.launch()` that logs a `warn`-level message to Cloud Logging whenever an autonomous ticket is dispatched without a "Relevant files" section in its content. The warning includes the ticket key and a plain-English message. Dispatch is not blocked — it is advisory only.

## Why it matters

MS-300 made "Relevant files" mandatory in the autonomous ticket spec and updated the briefing prompt to skip file enumeration entirely, relying on the ticket to supply that context. Without a runtime check, a ticket missing the section would silently dispatch a worker that starts without file guidance — and the briefing (Haiku) has no codebase access to compensate. The warning makes the gap visible in Cloud Logging before the worker runs blind.

## Where the change lives

- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — three-line check at the top of `launch()`, before `basePrompt` is built
- `agent/src/test/kotlin/com/mediasage/agent/JobDispatchTest.kt` — three new tests: missing section still dispatches, present section dispatches normally, null content dispatches normally

## Pattern

Case-insensitive `String.contains("relevant files")` — no regex, no heading parser. The section heading variations that could appear in Jira markdown ("## Relevant files", "**Relevant files**") all contain the substring, so a simple contains is sufficient and won't false-positive on typical ticket prose.
