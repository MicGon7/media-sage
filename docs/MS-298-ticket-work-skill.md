# MS-298: Introduce /ticket-work skill and outcome-based AC guidance

## What was built

A `/ticket-work` skill (`.claude/commands/ticket-work.md`) that gives autonomous workers an explicit, imperative workflow for ticket work — the most common job type. Previously, workers relied on `CLAUDE.md` Agent Guidelines for workflow steps and read shell commands from ticket AC checkboxes, which caused the wrong test command to run inside the container.

Two supporting changes to `CLAUDE.md`:
- Autonomous ticket requirements now state that AC must describe **outcomes, not commands**, with an example. Shell commands in AC leak into the Haiku-generated briefing and override the skill's instructions.
- Both bootstrap prompts in `AgentLaunchService.kt` now end with `/ticket-work`, ensuring the skill is always invoked — consistent with how `/conflict-resolution` works.

## Why it matters

Observed in MS-296 Cloud Run logs: the briefing included "Run `./gradlew :agent:test`" (copied verbatim from AC) as step 5. The worker followed it instead of `run-affected-tests.sh`. For a KDoc-only ticket this ran the full agent test suite unnecessarily. The root cause is that AC was carrying two concerns — outcome verification and workflow mechanics — that belong in different places.

The fix separates them cleanly: AC describes what done looks like; the skill describes how to get there.

## Key decision

The ticket proposed demoting "Relevant files" from mandatory to optional. That change was declined — the previous decision (MS-301) to keep it mandatory stands. Relevant files remain a hard requirement for autonomous tickets.

## Pattern

This completes the MS-281 pattern for the two main job types:

| Job type | Skill | Bootstrap appends |
|---|---|---|
| Conflict resolution | `/conflict-resolution` | Yes (explicit in prompt) |
| Ticket work | `/ticket-work` | Yes (appended to `BOOTSTRAP_PROMPT_*`) |

Adding new job types follows the same structure: create a `.claude/commands/<job-type>.md` skill and append the invocation to the relevant prompt constant in `AgentLaunchService.kt`.
