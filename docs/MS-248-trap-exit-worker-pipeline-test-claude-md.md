# MS-248 — trap EXIT in Worker + pipeline-test CLAUDE.md Updates

## Problems Fixed

### 1. Silent worker failures left Supabase jobs stuck as RUNNING

When `worker-entrypoint.sh` exited early (e.g. GitHub token generation failure), the
Pub/Sub block at the bottom of the script was never reached. The orchestrator received
no signal, and the Supabase job stayed `RUNNING` indefinitely. The only recovery path
was an orchestrator restart triggering `recoverInterruptedJobs()`.

Discovered during MS-247 smoke test: the worker failed with a GitHub 404
(stale `GITHUB_APP_INSTALLATION_ID` on the Cloud Run Job), left the job stuck, and
blocked all re-dispatch attempts until a manual restart.

### 2. pipeline-test tickets generated unnecessary docs and Confluence entries

The MS-247 smoke test worker generated a `docs/` learning doc for a trivial KDoc task
that existed only to exercise the pipeline. These tickets are about verifying
infrastructure, not producing artifacts.

## What Changed

### `agent/worker-entrypoint.sh`

Extracted the Pub/Sub publish block into a `publish_completion()` function and
registered it with `trap 'publish_completion $?' EXIT` near the top of the script —
before any early-exit can occur.

**Before:** Pub/Sub block at the bottom; unreachable on early exit.

**After:** `trap EXIT` fires on every exit path — token failure, `set -e` mid-run,
normal Claude Code completion. The function uses the GCP metadata server for auth,
which is always available in Cloud Run regardless of whether `GITHUB_TOKEN` was
successfully generated.

Also fixed a pre-existing typo: `exit $CLAUDE_EXIt` → the inline block and explicit
`exit` are now gone entirely; the trap owns the exit signal.

### `CLAUDE.md`

Two additions to Agent Guidelines:

1. **Workflow step 7**: Added note that `pipeline-test` tickets skip the learning doc step.
2. **After a PR is merged**: Added note that `pipeline-test` and `smoketest` tickets
   are excluded from the Confluence Agentic Development Impact doc.

## Key Learning

`trap EXIT` in bash is the idiomatic way to guarantee cleanup or notification runs
regardless of exit path. It's analogous to Android's `StateFlow` — declared once,
fires on any state change. Without it, any code at the bottom of a script is
unreachable on early exits caused by `set -e` or explicit `exit 1` calls.

The trap receives the actual exit code via `$?` at the time it fires — with `set -e`,
that's the exit code of the failing command, which correctly maps to `status=failure`
in the Pub/Sub payload.
