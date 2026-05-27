# :pipelineScenarios

Demo-ready, on-demand E2E test harness for the Media Sage agent pipeline.

Each Gradle task dispatches a real Cloud Run Job (or exercises real Supabase), waits for
completion, and prints a structured validation report. Use it for demos, pre-deploy verification,
and on-demand pipeline health checks.

## Available tasks

```bash
./gradlew :pipelineScenarios:tasks  # list all e2e* tasks with descriptions
```

### Dedup scenarios — require `SUPABASE_DB_URL` only

| Task | What it validates |
|---|---|
| `e2eDedupCompleted` | COMPLETED job permanently blocks a second dispatch — **post-deploy canary** |
| `e2eDedupRunning` | RUNNING job blocks a concurrent dispatch |
| `e2eDedupFailedRetry` | FAILED job is eligible for re-dispatch |

### Full pipeline scenarios — require Supabase + GCP credentials

| Task | What it validates |
|---|---|
| `e2eConflictResolution` | PR ejected from merge queue → resolver dispatched → branch rebased → review re-requested |
| `e2ePrReviewResponse` | `changes_requested` review → agent dispatched → fix committed → review re-requested |
| `e2eFailureRecovery` | Orchestrator restart with orphaned RUNNING job → `recoverInterruptedJobs()` → INTERRUPTED |

## Running a scenario

```bash
# Post-deploy canary (cheapest — Supabase only, no Cloud Run)
SUPABASE_DB_URL=... ./gradlew :pipelineScenarios:e2eDedupCompleted

# Full pipeline scenario
SUPABASE_DB_URL=... \
GCP_PROJECT_ID=... \
GOOGLE_CREDENTIALS_BASE64=... \
E2E_TICKET_KEY=MS-42 \
E2E_PR_NUMBER=42 \
E2E_BRANCH_REF=feature/MS-42-my-feature \
./gradlew :pipelineScenarios:e2eConflictResolution
```

Or source your env vars first:
```bash
source ~/.zshrc && ./gradlew :pipelineScenarios:e2eConflictResolution
```

## Required environment variables

| Variable | Scenarios | Notes |
|---|---|---|
| `SUPABASE_DB_URL` | All | PostgreSQL connection string |
| `GCP_PROJECT_ID` | Full pipeline | GCP project ID |
| `GCP_REGION` | Full pipeline | Default: `us-central1` |
| `GCP_JOB_NAME` | Full pipeline | Default: `media-sage-agent-worker` |
| `GOOGLE_CREDENTIALS_BASE64` | Full pipeline | Base64-encoded GCP service account JSON |
| `AGENT_REPO_PATH` | Full pipeline | Path to the cloned repo on the worker |
| `E2E_TICKET_KEY` | Full pipeline | Jira ticket key (e.g. `MS-42`) |
| `E2E_PR_NUMBER` | Full pipeline | GitHub PR number |
| `E2E_BRANCH_REF` | Full pipeline | Branch the worker should operate on |

## Validation report format

Each scenario prints a structured report to stdout:

```
═════════════════════════════════════════════════════════
 Pipeline Scenario: Conflict Resolution
═════════════════════════════════════════════════════════
 ✅ Cloud Run Job dispatched
 ✅ Job reached terminal state (COMPLETED)
 ✅ Job COMPLETED in Supabase

 PASS ✅
═════════════════════════════════════════════════════════
```

A non-zero exit code is returned on any checkpoint failure — making the task compatible
with CI pipelines and the `deploy-orchestrator.yml` canary step.

## Adding a new scenario

1. Create a test class in `src/test/kotlin/com/mediasage/pipeline/` extending
   `DedupScenarioBase` or `FullPipelineScenarioBase`
2. Annotate with `@Tag("e2e")` and `@Test`
3. Register a named task in `build.gradle.kts` under `scenarios`
4. Document it in this README
