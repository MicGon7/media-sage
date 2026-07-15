# MS-262: Pipeline Portability — Second Target (PIPE)

> **Status (MS-558, retired):** The live PIPE instance — the `agent-orchestrator-pipe`
> Cloud Run Service, the `pipeline-sandbox` GitHub repo, and the dedicated PIPE Supabase
> project — has been retired. Running two orchestrator environments in parallel proved to
> be sync overhead (two services, two Supabase projects, two secret sets) that delivered
> little beyond what CI unit tests, the `:pipelineScenarios` e2e harness, and `pipeline-test`
> smoke runs already cover. **The portability claim stands** — it was demonstrated
> end-to-end and is codified in this doc. Nothing in the pipeline hardcodes a target; a new
> client is still just a new Jira + GitHub + Supabase triple plus a Cloud Run Job definition.
> Standing up a live second target again requires only re-creating those resources and a
> `TargetConfig` entry in `pipelineScenarios/build.gradle.kts`. The sections below are kept
> as the record of what was built and verified.

## What Was Built

Made the agentic pipeline demonstrably reusable by pointing it at a second independent target:
a PIPE Jira project, a `pipeline-sandbox` GitHub repo, and a dedicated Supabase instance.
A second Cloud Run Service (`agent-orchestrator-pipe`) was deployed alongside the existing
`media-sage-orchestrator`. All six E2E pipeline scenarios now run against both targets.

## Key Learning: Per-Target Job Definitions

The original approach injected per-target values (`GITHUB_REPO`, `GITHUB_OWNER`, `PUBSUB_TOPIC`)
into the worker at dispatch time via Cloud Run's `containerOverrides.env`. This hit a fundamental
Cloud Run constraint: **override env vars append to the static job definition — they do not
replace it.** If the same key exists in both the static job and the per-run override, the static
value wins silently.

The correct approach is to treat the Cloud Run Job definition as the unit of configuration for a
target. Two job definitions, same container image:

| Job | GITHUB_REPO | PUBSUB_TOPIC |
|---|---|---|
| `media-sage-agent-worker` | `media-sage` | `cloud-run-job-completions` |
| `pipe-agent-worker` | `pipeline-sandbox` | `cloud-run-job-completions-pipe` |

The orchestrator only injects truly dynamic per-run values at dispatch time: `PROMPT`,
`TICKET_KEY`, and `JIRA_TICKET_KEY`. Everything structural lives in the job definition.

Each orchestrator service sets `GCP_JOB_NAME` to point at its job. Adding a new client means
creating a new Cloud Run Job with their config baked in — no code changes required.

## Rule: Never Put Per-Target Values in the Static Job Definition

`containerOverrides.env` is designed for per-invocation dynamic inputs (prompts, ticket keys).
Using it for structural target wiring (which repo to clone, which topic to publish to) goes
against the Cloud Run design and is unreliable due to the append behavior described above.

This rule is documented in CLAUDE.md under `### Code` conventions.

## Infrastructure Per Target

Each pipeline target requires:

| Component | MS | PIPE |
|---|---|---|
| Jira project | `MS` | `PIPE` |
| GitHub repo | `media-sage` | `pipeline-sandbox` |
| Supabase instance | MS Supabase | PIPE Supabase (separate project) |
| Pub/Sub topic | `cloud-run-job-completions` | `cloud-run-job-completions-pipe` |
| Pub/Sub push subscription | → MS orchestrator `/webhook/pubsub` | → PIPE orchestrator `/webhook/pubsub` |
| Cloud Run Job | `media-sage-agent-worker` | `pipe-agent-worker` |
| Cloud Run Service | `media-sage-orchestrator` | `agent-orchestrator-pipe` |

The Pub/Sub topic separation is important: each topic has its own push subscription pointing at
its orchestrator. Without this, completion events from PIPE workers would be delivered to the MS
orchestrator (or vice versa) and silently ignored.

## Worker Entrypoint Is Now Configurable

`worker-entrypoint.sh` reads `GITHUB_OWNER` and `GITHUB_REPO` from env (with `media-sage`
defaults). This means the same worker image can clone any repo the GitHub App has access to.
The GitHub App (`media-sage-worker`) must be installed on any target repo.

## E2E Scenario Symmetry

All six pipeline scenarios now have both MS and PIPE variants:

| Scenario | MS task | PIPE task |
|---|---|---|
| Dedup: skip RUNNING | `e2eDedupRunning` | `pipeE2eDedupRunning` |
| Dedup: block COMPLETED | `e2eDedupCompleted` | `pipeE2eDedupCompleted` |
| Dedup: retry FAILED | `e2eDedupFailedRetry` | `pipeE2eDedupFailedRetry` |
| PR review response | `e2ePrReviewResponse` | `pipeE2ePrReviewResponse` |
| Conflict resolution | `e2eConflictResolution` | `pipeE2eConflictResolution` |
| Failure recovery | `e2eFailureRecovery` | `pipeE2eFailureRecovery` |

The three full-pipeline PIPE scenarios were verified end-to-end during this ticket:
`pipeE2ePrReviewResponse` and `pipeE2eConflictResolution` both passed against live GCP
infrastructure. The three dedup and failure recovery PIPE tasks use the same test classes as
their MS counterparts — only the target config differs.

## Jira Spaces vs Jira Projects

When setting up the PIPE Jira project, the Atlassian sidebar shows "Spaces" (a Confluence
concept) separate from Jira projects. A Jira project lives under Jira → Projects → Create project.
Creating a Space in Confluence does not create a Jira project. Both share the same Atlassian
cloud ID — the cloud ID identifies the organization, not the product.

## Supabase Connection String

The Supabase project URL (`https://xxx.supabase.co`) is not a valid PostgreSQL connection string.
Use Project Settings → Database → Session Pooler → Connection string (URI format) for the
`SUPABASE_DB_URL` env var. Session pooler is required for Cloud Run (serverless) connections.
