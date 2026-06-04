# MS-303: Clarify CLAUDE.md vs skills ownership — rules stay, workflow steps move to skills

## What was built

A clean separation of concerns across the three layers every worker interacts with:

- **Prompt** — context (job-specific: ticket key, PR number, branch, comment text)
- **Skill** — instructions (how to execute the job)
- **CLAUDE.md** — rules (standing constraints that apply across all jobs)

### CLAUDE.md changes

The `### Workflow` numbered steps (12 steps covering branching, implementing, committing, PRs, Jira comments) were removed from `## Agent Guidelines`. They belonged in skills, not here.

Replaced with:
1. A one-paragraph explanation of the three-part model
2. A `### Rules` section containing only standing constraints: tests, OOM stop rule, blocker stop rule, no secrets, never push to main, smoke test external APIs

### Skill changes

All four skills are now fully self-contained:

- `/ticket-work` — embeds the full jira comment format, quality gate steps, commit convention, and Jira transition step. No implicit reliance on CLAUDE.md for instructions.
- `/pr-review` — embeds quality gate steps and jira comment format reference. Self-contained.
- `/pr-comment` — already minimal; deferral line scoped to rules only.
- `/conflict-resolution` — already minimal; deferral line scoped to rules only.

Each skill ends with exactly one line: "Follow the Agent Guidelines in CLAUDE.md for standing rules (no pushing to main, no secrets, stop-and-comment-if-blocked, OOM rule)."

## Why it matters

Before this change, workers received the same workflow steps twice — once from CLAUDE.md and once from the skill. More importantly, CLAUDE.md was carrying executable instructions that could conflict with skill steps. The separation eliminates ambiguity: if a worker needs to know how to do something, it reads the skill. If it needs to know what it must never do, it reads CLAUDE.md.

## The model

```
Prompt   → what the job is       (ticket key, PR number, comment text)
Skill    → how to execute it     (.claude/commands/<job-type>.md)
CLAUDE.md → what the rules are   (## Agent Guidelines → ### Rules)
```
