# Autonomous Mode

An **autonomous agent** runs the full workflow — Jira, branch, code, tests, docs, commit, PR — without human interaction. The human's only touchpoint is the GitHub PR review.

## Trigger model (Level 2 — Autonomous)

The Jira webhook fires when a ticket is **assigned to the bot account** and its status transitions to **In Progress**. Both conditions must be true — labels play no role in firing the agent. The GitHub webhook fires when a `pull_request_review` or `pull_request_review_comment` event arrives for a branch whose ticket key is an open Jira issue.

## Jira labels

Labels are kept for historical tracking and query filtering, not to control pipeline behavior:

- **`autonomous`** — historical tag indicating the agent ran the full workflow; useful for filtering reporting queries
- **`assisted`** — tag indicating a human worked the ticket with AI assistance; the expected default for all AI-involved work, though its long-term reporting value is still being evaluated

Neither label gates or triggers any pipeline action.

## Invocation

```bash
# Assisted — human approves tool calls (interactive Claude Code session)
claude

# Autonomous — bootstrap command (always the same, ticket is the prompt)
claude -p "Check Jira (cloudId: media-sage.atlassian.net) for the next ticket labeled 'autonomous' in project MS with status 'To Do'. Read the ticket description and acceptance criteria for your task. Follow the Agent Guidelines in CLAUDE.md and execute the full workflow." \
  --dangerously-skip-permissions
```

The bootstrap command never changes — the **ticket is the prompt**. Every autonomous ticket must include a clear task description and explicit acceptance criteria so the agent has everything it needs without human input.

## Job-type skills

Each job type dispatched by the orchestrator ends its bootstrap prompt with a skill invocation (e.g. `/conflict-resolution`). Skills live in `.claude/commands/` and are committed to the repo — workers pick them up automatically on clone. The skill contains the imperative workflow steps for that job type; the bootstrap prompt supplies the job-specific context (branch, ticket, PR number). This separates *what the job is* (prompt) from *how to execute it* (skill), and allows workflow steps to be updated without redeploying the orchestrator image.

Current skills:
- `/conflict-resolution` — rebase a branch ejected from the merge queue and re-request review
- `/ticket-work` — execute the full ticket work workflow (branch, implement, test, detekt, PR, Jira comment)
- `/pr-review` — respond to a PR review comment: fix code (or explain why not), push, re-request review
- `/pr-comment` — answer a conversational PR comment via a reply; no code push

## Autonomous ticket requirements

- Title: concise task description (agent uses this as the task summary)
- Description: what needs to change and why
- Acceptance criteria: explicit checkboxes the agent checks off as it works
- Relevant files: **mandatory** — list the 3–5 files the agent should read first, each with a one-line note on why it matters. This is the primary way context is passed to the worker; the briefing skips file enumeration entirely and relies on this section being present. A ticket without a relevant files section is not ready for autonomous mode.
- Acceptance criteria: describe **outcomes, not commands** — Haiku reads AC as briefing input, so shell commands in AC leak into the dispatch prompt and conflict with the `/ticket-work` skill. Good: "The foo field is validated at the repository boundary." Bad: "Run `./gradlew :shared:test`."
- Label: `autonomous`
- No ambiguous requirements — if it needs clarification, use `assisted` instead
- Tickets that touch `.github/workflows/`, `Dockerfile.worker`, or `agent/worker-entrypoint.sh` must use `assisted` — these files define the pipeline itself, the worker cannot push workflow files without elevated permissions, and mistakes here have wide blast radius

## Automation levels

- **Level 1 — Assisted**: Human works interactively with Claude Code in any configuration (auto-accept, plan mode, or with tool approvals). The configuration doesn't define the level — the human's presence does. They can steer, redirect, and co-author at any point. This is AI-augmented pair programming.
- **Level 2 — Autonomous**: Jira webhook fires when a ticket is assigned to the bot account and moved to In Progress. The orchestrator dispatches a Cloud Run Job. The worker runs the full workflow autonomously. The human's only touchpoint is the PR review.

  The full PR lifecycle is part of Level 2 — not a separate level:
  - **PR review comments** → GitHub webhook → orchestrator dispatches a worker → fix commit + re-request review
  - **Merge queue conflict** → GitHub webhook → orchestrator dispatches a worker → rebase + re-request review

  The human's touchpoint (PR review) never changes regardless of how many review cycles occur.

_This project is at Level 2. Both the Jira webhook (`POST /webhook/jira`) and the GitHub webhook (`POST /webhook/github`) are live in the `:agent` module, deployed as a Cloud Run Service on GCP. See `docs/diagrams/agent-pipeline.md` for the full flow diagram._

See `agent/CLAUDE.md` for deployment config, env vars, webhook URLs, job registry schema, and local dev setup.

## Autonomous vs Assisted

| | Autonomous | Assisted |
|---|---|---|
| Human touchpoints | PR review only | Present throughout |
| Speed | Minutes | Hours |
| Best for | Well-defined tasks with proven patterns | Exploratory work, new architecture, ambiguous requirements |
| Risk | Mistakes reach PR before any human sees them | Human can course-correct mid-run |

## When to use autonomous mode

- Task is well-scoped with a clear acceptance criterion
- All patterns already exist in the codebase (no novel architecture decisions)
- The diff will be small enough for a reviewer to catch any mistakes

## When NOT to use autonomous mode

- First implementation of a new pattern (e.g., new data layer, new nav pattern)
- Tasks requiring external smoke tests (live API calls, device testing)
- Anything that touches database migrations, security, or auth
- Ambiguous tasks where requirements need clarification

## Pros

- Executes the full workflow in minutes with no context switching for the developer
- Enforces consistency — never skips detekt, tests, docs, or Jira updates
- Scales horizontally — multiple agents can work different tickets in parallel

## Cons

- Mistakes run all the way to a PR before anyone sees them — good PR review hygiene is essential
- Requires pre-approved tool permissions (trust boundary is the whole session)
- Context window limits: very large tasks may need to be broken into smaller tickets
- No mid-run judgment — if the task turns out to be more complex, the agent may produce incomplete work rather than stopping to ask
