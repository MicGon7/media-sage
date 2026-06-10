# Tool Consolidation Pattern for Autonomous Workers

## Why mechanical steps belong in scripts, not model turns

Each tool call a worker makes costs a model turn. A model turn is not free — it consumes tokens, adds latency, and introduces another opportunity for the model to pause, narrate, or make an unnecessary decision. For deterministic, mechanical steps this is pure waste.

The original worker flow had ~15 turns because every discrete action was a separate tool call:

1. `gh pr list` (read)
2. `git fetch && git checkout` (bash)
3. Read relevant files (read ×N)
4. Implement change (edit ×N)
5. `run-affected-tests.sh` (bash)
6. `./gradlew detekt` (bash)
7. Read `/tmp/detekt.log` to check result (read)
8. `git stash && ./gradlew detekt && git stash pop` if it failed (bash)
9. Curl GET ADF (bash)
10. Python patch checkboxes (bash)
11. Curl PUT ADF (bash)
12. `git add && git commit && git push` (bash)
13. Write `/tmp/pr_body.md` (write)
14. `gh pr create` (bash)
15. Curl POST Jira transition (bash)

Steps 1–2, 5–8, and 9–15 are fully deterministic. They make no decisions that require model judgment. Moving them into scripts reduces the turn count to ~7:

1. `worker-init.sh` — branch setup (1 turn)
2. Read relevant files (read ×N)
3. Implement change (edit ×N)
4. `worker-quality.sh` — tests + detekt (1 turn)
5. Write `/tmp/pr_body.md` + `/tmp/jira_comment.txt` (write ×2)
6. `worker-ship.sh` — commit, push, PR, Jira AC, transition (1 turn)

## The pattern

> **If a sequence of steps has no branching that requires model judgment, collapse it into a bash script.**

Signals that a step belongs in a script:
- It always runs the same commands in the same order
- The only "decision" is pass/fail (handled by `set -euo pipefail` or an `if [ $? -ne 0 ]` guard)
- It produces a side effect (a file, a git state, an API call), not insight

Signals that a step should stay as a model turn:
- It reads output and decides what to do next based on content
- It makes a judgment call that depends on ticket context
- It writes code, prose, or structured data from scratch

## The three worker scripts

### `scripts/worker-init.sh TICKET_KEY BRANCH_DESCRIPTION`

Handles the "is work already in flight?" check and branch creation in a single turn. Writes `/tmp/worker_init.env` so the worker can `source` the result and branch conditionally without a second read turn.

### `scripts/worker-quality.sh`

Runs `run-affected-tests.sh` and `./gradlew detekt` in parallel with `tee` so output is captured inline. If detekt fails it automatically runs the pre-existing violation check (`git stash → detekt → git stash pop`) and distinguishes new violations from inherited ones. The worker gets a single clear pass/fail with no follow-up read needed.

### `scripts/worker-ship.sh TICKET_KEY "COMMIT_MSG"`

Performs every post-implementation mechanical step atomically: commit, push, PR creation, Jira AC checkbox update (GET ADF → patch → PUT), and Jira transition to In Review. The PR URL is written to `/tmp/worker_pr_url.txt` for the Jira comment step.

## Applying this pattern to future pipeline work

When adding a new job type (a new skill under `.claude/commands/`), audit the proposed flow before writing it:

1. List every tool call
2. Mark each one: **judgment** (stays as a turn) vs **mechanical** (candidate for a script)
3. Group consecutive mechanical steps into a single script

The target is ≤ 10 turns for any job type. Below 7 is achievable for straightforward implementation tasks. Turns 1–3 are almost always judgment (read ticket, read code, write code); everything else is usually consolidatable.

This pattern also improves reliability. A script that `set -euo pipefail`s fails fast with a clear message. A model that encounters an error in the middle of a multi-step bash block may narrate around it or retry in ways that leave the repo in a partial state.
