# MS-269: Fix e2e-base branch isolation

## What was built

Two fixes to prevent the conflict resolution E2E scenario from wasting worker turns on a no-op rebase.

### Fix 1 — Per-run base branch isolation

`ConflictResolutionE2eTest` previously pushed a conflict commit to the shared `e2e-base` branch in `BeforeEach` but never cleaned it up in `AfterEach`. Over multiple runs, stale commits accumulated. The next run's `syncBranchWithMain` force-reset `e2e-base` to main — but if runs overlapped or the previous cleanup was skipped, the worker could see a different file on `e2e-base` than what the current test had pushed. Different filenames = no conflict = 33 wasted turns ($0.82).

**Fix:** Replace the shared `e2e-base` branch with a unique per-run branch (`e2e-base-{shortId}`). `BeforeEach` creates it from main via `syncBranchWithMain` (which handles create-if-not-exists). `AfterEach` deletes both the feature branch and the per-run base branch. No shared state, no race window.

This is the idiomatic pattern for fixture isolation: eliminate sharing rather than patching teardown order.

### Fix 2 — Conflict resolution prompt early-exit

`CONFLICT_RESOLUTION_PROMPT` in `AgentLaunchService` now opens with an explicit early-exit instruction: if `git rebase` produces no changes and no conflicts, write one line to `/tmp/jira_comment.txt` and exit immediately. The worker must not investigate further, open a PR, or spend additional turns.

This caps the blast radius of any future fixture misfires or real merge queue conflicts that resolve themselves before the worker starts — from 33+ turns down to ≤5.

## Key decisions

- **Option B over Option A**: The ticket listed Option A (reset `e2e-base` in `AfterEach`) as preferred, but Option B (unique per-run branch) is the idiomatic choice. Option A still leaves a race window when runs overlap; Option B eliminates shared state entirely.
- **No new helpers in `GitHubFixtureClient`**: `syncBranchWithMain`, `createBranch`, and `deleteBranch` already composed correctly for the new pattern. No changes needed to the fixture client.
- **`E2E_BASE_BRANCH` constant retained**: Still used by `PrReviewResponseE2eTest` and as a default param on `openPullRequest`/`createBranch`.

## Files changed

- `pipelineScenarios/src/test/kotlin/com/mediasage/pipeline/pipeline/ConflictResolutionE2eTest.kt` — per-run `baseBranch`, updated setup/teardown/webhook payload
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — early-exit instruction in `CONFLICT_RESOLUTION_PROMPT`
