# MS-148: Structured Milestone Logging for Claude Code Agent

## What we built

Replaced raw stream-json line logging in `AgentLaunchService` with a milestone filter that emits one human-readable log line per meaningful agent action, suppressing all token-level events.

## Key decisions

### StreamJsonParser as a standalone internal function

The parsing logic lives in `StreamJsonParser.kt` as a package-level `internal` function `parseStreamJsonMilestone(line: String): String?`. Returning `null` means suppress; returning a string means log it. This makes the function independently testable without needing to wire up a full service, a subprocess, or coroutines.

### Events surfaced vs suppressed

Surfaced:
- `system` init → `init: model=<name>`
- `assistant` tool_use → `tool: <Name> — <command or description>`
- `assistant` text block → `thinking: <first 80 chars>`
- `user` tool_result with `is_error: true` → `tool error: <detail>`
- `result` → `done — <subtype> <duration>ms $<cost>`

Suppressed: everything else — `content_block_delta`, `message_delta`, `message_start`, `message_stop`, `content_block_start`, `content_block_stop`, and any unrecognised type.

### AGENT_LOG_VERBOSE escape hatch

Setting `AGENT_LOG_VERBOSE=true` in Railway (or locally) bypasses the filter entirely and logs every raw stream-json line. Useful when the agent is failing silently and you need the full output to diagnose the root cause.

### stderr is always logged at WARNING

Regardless of verbose mode, stderr lines from the Claude Code subprocess are always logged at WARNING level. These are typically process-level errors (bad command, missing env var) rather than tool output, so suppressing them would hide real failures.

### Detekt: extract helpers to stay under LongMethod and CyclomaticComplexMethod

The initial single-function parser triggered `LongMethod` (57 lines, max 30) and `CyclomaticComplexMethod` (complexity 27, max 15). Extracting `parseSystemEvent`, `parseAssistantEvent`, `parseUserEvent`, `parseResultEvent`, `parseContentBlock`, `parseToolUseBlock`, `parseTextBlock`, and `parseToolResultBlock` brought both metrics under threshold.

## What we learned

- The stream-json format has a clear event taxonomy: `system`, `assistant`, `user`, `result`. Everything else is streaming noise. Filtering on these four types captures all meaningful milestones.
- `content_block_delta` events (token streaming) account for the vast majority of the output volume — suppressing them reduces log volume by ~90%.
- A `null`-returning parse function is a clean pattern for "suppress or emit" logic — no separate allow/deny list needed.
- kotlinx.serialization's `jsonObject`, `jsonArray`, `jsonPrimitive` throw on type mismatch — a top-level try-catch in the entry point handles all malformed or unexpected shapes gracefully.
