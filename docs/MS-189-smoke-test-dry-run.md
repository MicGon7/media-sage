# MS-189 — Smoke Test Dry-Run Flag

## Problem

The MS-179 dedup smoke test had a hidden side effect: cases 3 (FAILED → retry) and 4 (INTERRUPTED → retry) trigger a real Cloud Run dispatch after verifying the PENDING row insertion. The orchestrator then enters a `pollUntilDone` loop with a 30-minute timeout (`JOB_TIMEOUT_MS = 1_800_000L`), polling every 30 seconds regardless of whether the job ever completes.

During a local test run, this caused:
- The orchestrator to log "Cloud Run job still running..." for minutes after the test passed
- A real Cloud Run worker to spin up, read the fake ticket key (`MS-SMOKE-DEDUP-<pid>`), and exit only after identifying it as malformed
- Potential token consumption and rate-limiting side effects on other Claude sessions

## Solution

Added a `--dry-run` flag to the smoke test and an `X-Dry-Run: true` header pathway through the agent stack.

### How it works

**Script (`scripts/smoke-test-dedup.sh`):**
- Parses `--dry-run` flag on startup
- Cases 1 and 2: unchanged (no dispatch occurs anyway — dedup blocks them)
- Cases 3 and 4: passes `X-Dry-Run: true` header to the webhook call

**Webhook route (`JiraWebhookRoutes.kt`):**
- Reads `X-Dry-Run` header
- Passes `dryRun = true` to `agentService.launch()`

**Launch service (`AgentLaunchService.kt`):**
- `launch()` accepts `dryRun: Boolean = false`
- `dispatchToCloudRun()` runs the full dedup check and inserts the PENDING row
- If `dryRun = true`, immediately marks the job FAILED and returns — no `executeJob()` call, no polling

This means dry-run mode fully exercises the dedup logic (including the DB state machine) but stops before handing off to Cloud Run.

## Usage

```bash
# Local testing — safe, no Cloud Run dispatch, no polling
./scripts/smoke-test-dedup.sh --dry-run

# Production smoke test against Railway — real dispatch, use sparingly
./scripts/smoke-test-dedup.sh
```

## Gotcha: Don't use `eval` to conditionally add curl flags

The initial implementation used `eval` to conditionally inject the `X-Dry-Run` header:

```bash
status_code=$(eval curl ... $dry_run_header -d "{...}")
```

`eval` caused the JSON body to be interpreted as shell commands, producing errors like `webhookEvent:: command not found`. The fix was to use a bash array instead:

```bash
local curl_args=(-s -o /dev/null ...)
if [ "$use_dry_run" = "true" ]; then
    curl_args+=(-H "X-Dry-Run: true")
fi
curl "${curl_args[@]}" -d "{...}"
```

Arrays are the idiomatic bash pattern for building dynamic argument lists safely.

## Key Learnings

- **Always add a dry-run mode to tests that trigger external side effects.** The smoke test was designed to verify dedup logic, not to dispatch real jobs — but it did so implicitly.
- **The orchestrator's polling loop is async.** The script exits after verifying row counts, but the agent's coroutine keeps running. This is invisible from the test output.
- **Cloud Run workers have their own safety check.** The worker correctly identified the malformed ticket key and exited cleanly. Token usage was minimal (8 input + 323 output tokens). But this is not a reliable safety net — a future test with a valid-but-stale ticket key could spin up a full agent run.
- **Default behavior is unchanged.** Running without `--dry-run` still dispatches to Cloud Run, which is correct for production smoke tests against a real Railway deployment.
