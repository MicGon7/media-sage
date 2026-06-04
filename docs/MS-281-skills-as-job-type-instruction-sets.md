# MS-281: Claude Code Skills as Job-Type-Specific Worker Instruction Sets

## What was built

Introduced Claude Code skills (`.claude/commands/`) as the home for job-type-specific worker procedure. The first skill, `/conflict-resolution`, replaces the inline procedure that was previously embedded in `CONFLICT_RESOLUTION_PROMPT` inside `AgentLaunchService.kt`.

The `.gitignore` was updated to commit `.claude/commands/` to the repo while keeping the rest of `.claude/` (personal settings, session data) ignored.

## Key decisions

### Skills vs. briefing — complementary, not competing

The BriefingService generates *context* — a Haiku-summarised briefing of what this specific job is about. A skill provides *procedure* — the imperative steps for how to execute a job type. They answer different questions and both remain in the final prompt.

### No `$ARGUMENTS` — orchestrator controls the full prompt

`$ARGUMENTS` is designed for human-typed invocations. Since the orchestrator builds the bootstrap prompt programmatically, the job-specific details (branch, ticket, PR number) live in the prompt body above the skill invocation. The skill is appended last as a bare `/skill-name` with no arguments.

The worker's final prompt shape:
```
Branch feature/MS-123 for ticket MS-123 was ejected from the merge queue due to a conflict with main. PR #456.

## Agent Briefing
...Haiku-generated context...

/conflict-resolution
```

### Skills committed to the repo — the key value

The primary motivation is operational: updating a workflow step (e.g. adding a no-op guard, changing the comment format) previously required editing a Kotlin string constant, rebuilding the orchestrator image, and redeploying. With skills in the repo, workers pick up changes on the next clone — no orchestrator redeploy needed.

### `.gitignore` pattern: `.claude/*` not `.claude/`

Negation patterns (`!.claude/commands/`) don't work when the parent directory is fully ignored with a trailing slash (`.claude/`). The fix is to ignore the *contents* with a glob (`.claude/*`), which allows negation of specific subdirectories.

## Verification

`-p "/skill-name"` was confirmed to trigger skill invocation in non-interactive mode by creating a minimal test skill and running `claude -p "/hello" --print`. Output matched the skill's instruction exactly.

## Files changed

- `.gitignore` — `.claude/` → `.claude/*` + `!.claude/commands/`
- `.claude/commands/conflict-resolution.md` — new skill
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — `CONFLICT_RESOLUTION_PROMPT` simplified to context header + `/conflict-resolution`
- `CLAUDE.md` — documents the job-type skills pattern and lists current skills

## What's next

The remaining job types (`PR_REVIEW_PROMPT`, `PR_COMMENT_REVIEW_PROMPT`, `BOOTSTRAP_PROMPT_WITH_CONTENT`) can be migrated to skills following the same pattern when needed. Each is a natural candidate once the conflict resolution skill is validated in production.
