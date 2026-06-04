# MS-295: Fix Worker Prompt Logging and E2E Test Teardown Race

## What Changed

Three small but impactful fixes to observability and e2e test determinism surfaced from inspecting
Cloud Run logs after the MS-281 conflict resolution run.

### 1. Prompt logged as a single unbroken entry (`worker-entrypoint.sh`)

`echo` splits on newlines — each line of the multi-line prompt became a separate Cloud Run log
entry, making the full prompt hard to read and the skill invocation appear as an orphaned fragment.
Replaced with `printf '%s\n'` which emits the entire string as one log entry.

The 500-character cap was also removed. The original cap predated the BriefingService; now that
prompts include hundreds of tokens of briefing plus a skill invocation at the end, 500 chars cut
off before any of that content was visible.

### 2. Token generation success explicitly logged (`worker-entrypoint.sh`)

Added `echo "GitHub App token generated successfully"` immediately after the token is exported.
Previously, a successful token generation produced no log output — only failures were visible.
This gives a clear confirmation line in Cloud Run logs before the repo clone begins.

### 3. Branch teardown moved after `waitForCompletion` (`ConflictResolutionE2eTest`)

`@AfterEach` ran unconditionally as soon as the test method returned, including on timeout. If the
test timed out or threw early, the branches were deleted while the Cloud Run Job was still running.
The worker then hit `fatal: invalid upstream 'origin/e2e-base-...'`, forcing extra recovery turns
and adding non-deterministic token usage.

Fix: branch deletion moved into the test body, after `waitForCompletion`. `@AfterEach` now only
closes the PR (idempotent, safe to run immediately). Branches live until the job reaches a
terminal state.

### 4. Logs Explorer queries documented (`agent/CLAUDE.md`)

Added two saved query blocks under a new "Cloud Run Logs Explorer Queries" section for clean demo
and debugging views of worker and orchestrator logs.

## What I Learned

- `echo` is line-based — it emits one log entry per newline. For multi-line values use `printf '%s\n'`.
- `@AfterEach` fires on any exit path including test failures and timeouts, so teardown that races
  background async work must be deferred inside the test body after the `await` call, not in the
  after hook.
- Explicit success logging is as valuable as failure logging in container workflows where the
  absence of a line is otherwise ambiguous.
