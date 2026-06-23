# MS-414: Restore env_startup_ms and fix transcript turn count

## What changed

Two regressions introduced when MS-412 deleted `CloudLoggingClient`:

1. **`env_startup_ms` always null** — the column was populated from the container's first-log timestamp, which only existed via `CloudLoggingClient`. With that deleted, there was no path to compute the value.

2. **Transcript turn count off by one** — the worker transcript's `## Turn N` counter was gated on `turn_parts` being non-empty. If an assistant event produced no displayable content (e.g. whitespace-only text), the event was silently skipped, causing the manual counter to diverge from Claude Code's `num_turns`.

## How they were fixed

### env_startup_ms

The fix moves timestamp ownership to the container itself. `entrypoint-common.sh` now captures a millisecond epoch timestamp at the very first line of executable code:

```bash
CONTAINER_STARTED_AT_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
```

This value flows through the Pub/Sub payload as `containerStartedAtMs`, is decoded into `JobCompletionEvent`, and then the orchestrator computes the gap:

```kotlin
val envStartupMs = event.containerStartedAtMs?.let { startedMs ->
    job.startedAt?.let { startedAt -> startedMs - startedAt.toEpochMilli() }
}
```

`job.startedAt` is the moment `markRunning()` was called — when the Cloud Run dispatch API call returned. The difference captures cold start + image pull time.

### Turn count

The counter now always increments for every `assistant` event (matching Claude Code's definition), and every turn always writes a section header:

```python
turn += 1
...
parts.append(f"\n---\n\n## Turn {turn}\n")
parts.extend(turn_parts if turn_parts else ["*(no output)*\n"])
```

Key insight from the fix discussion: `thinking` blocks are not separate turns — they appear alongside `text` or `tool_use` blocks in the same assistant event content array. They are internal reasoning, not a distinct agent turn. The actual output of a thinking step is the `text` or `tool_use` that follows in the same event. So expanding block type handling was not the answer; the fix was simply to stop gating the counter on output presence.

## What not to do

Do not gate `turn += 1` on whether the event produced displayable output. The counter should mirror Claude Code's `num_turns`, not the human-readable content filter.

Do not attempt to hide empty turns by skipping their section headers. Every turn Claude Code counted should appear in the transcript, even if the content falls back to `*(no output)*`.
