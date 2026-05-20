# MS-199 — Gate AgentBriefing Behind Feature Flag & Require Relevant Files in Tickets

## Why AgentBriefing Was Built

MS-195 introduced `AgentBriefing` to enrich the worker prompt before Cloud Run dispatch.
The idea: run a bounded `claude -p --max-turns 3` call in the orchestrator to identify
relevant files and patterns, so the worker skips codebase exploration and goes straight
to implementation.

## Why It's Paused

In practice, AgentBriefing consistently timed out (60s–180s) in the orchestrator context.
The root cause: without `--dangerously-skip-permissions`, claude prompts for tool permissions
and hangs (stdin is `/dev/null`). With the flag, each API round trip takes ~20-30s — 3 turns
exceeds any reasonable timeout. The one time it succeeded, claude answered from training
knowledge without tool calls, not from actual file inspection.

The net result: 60s of dead weight on every dispatch, with zero proven benefit to worker
speed. MS-167 completed correctly without any briefing.

## The Right Pattern at This Scale

The ticket author already knows which files are relevant — they wrote the ticket. That
context belongs in the ticket description as a `## Relevant Files` section, not in a
separate LLM call.

**What belongs in the ticket:**
```markdown
## Relevant Files
- `path/to/file.kt` — why this file matters for the implementation
- `path/to/test.kt` — where new test cases should go
```

This is reviewed by a human before the worker ever runs, costs nothing, and is immediately
readable during a PR review.

## Feature Flag

`AGENT_BRIEFING_ENABLED=false` (default) — AgentBriefing is wired but dormant.
Set `AGENT_BRIEFING_ENABLED=true` to re-enable for demos or future testing.

## When to Re-Enable AgentBriefing

AgentBriefing makes sense when:
- Tickets are intentionally vague and the orchestrator needs to fill in the gaps
- The codebase is large enough that relevant files aren't obvious to the ticket author
- The latency issue is resolved (e.g., a faster model, async pre-computation, or
  running briefing on the Cloud Run worker itself rather than in the orchestrator)

## Key Learnings

- **Prove the problem before building the solution.** We built AgentBriefing without
  measuring whether workers actually struggled with codebase exploration.
- **Ticket quality is infrastructure.** A well-written ticket with a Relevant Files
  section is simpler, cheaper, and more reliable than a dynamic briefing step.
- **Feature flags beat deletion.** AgentBriefing is a good demo concept and valid
  architectural pattern — pausing it behind a flag keeps the story intact.
