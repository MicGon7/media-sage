# MS-304 / MS-305: Introduce /pr-review and /pr-comment skills

## What was built

Two new skills completing the full set of job types the pipeline can execute:

- `.claude/commands/pr-review.md` (`/pr-review`) — handles PR review comments. Worker checks out the branch, reads the comment and relevant files, makes the fix (or posts an explanation if no change is needed), pushes, and re-requests review.
- `.claude/commands/pr-comment.md` (`/pr-comment`) — handles conversational PR comments. Worker reads the PR diff and answers the question via a PR comment. No code push.

Both prompts in `AgentLaunchService.kt` (`PR_REVIEW_PROMPT`, `PR_COMMENT_REVIEW_PROMPT`) now supply only job-specific context (PR number, ticket, comment, branch, reviewer) and end with the skill invocation. Inline workflow steps are gone.

## Why it matters

With these two skills, every job type the orchestrator can dispatch now has a corresponding skill in `.claude/commands/`:

| Job type | Skill | Prompt constant |
|---|---|---|
| Ticket work | `/ticket-work` | `BOOTSTRAP_PROMPT_*` |
| Conflict resolution | `/conflict-resolution` | `CONFLICT_RESOLUTION_PROMPT` |
| PR review comment | `/pr-review` | `PR_REVIEW_PROMPT` |
| Conversational PR comment | `/pr-comment` | `PR_COMMENT_REVIEW_PROMPT` |

This makes the architecture legible: `.claude/commands/` is the complete instruction set for every job the pipeline knows how to run. Workflow steps can be updated without redeploying the orchestrator image.

## Naming convention

Skills follow `noun` or `noun-noun` format — short and parallel. Suffixes like `-response` or `-answer` are redundant and were dropped.

## Known limitation

`/pr-comment` answers are limited to what is visible in the PR diff and available via the GitHub API. The orchestrator never clones the repo. Questions requiring broader codebase context are flagged to the reviewer in the response. This limitation will be partially addressed in MS-306 (intelligent dispatch decision-making).
