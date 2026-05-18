# MS-175: Cloud Run Worker Architecture

## What We Built

The agent orchestration system now supports two execution modes: local process (laptop/dev) and Cloud Run Jobs (production). A feature flag — `useCloudRunWorkers` — switches between them with no code changes to the webhook routing or dedup gate.

When Cloud Run mode is active, the Railway orchestrator receives a Jira webhook, deduplicates it using the existing `activeKeys` gate, and dispatches an ephemeral Cloud Run Job execution. The worker container clones the repo, runs `claude -p "$PROMPT"`, and exits. The orchestrator holds the dedup key for the full duration by polling the Cloud Run long-running operation (LRO) until completion.

## Architecture

```
Jira Webhook
     │
     ▼
Railway Orchestrator (:agent)
  - Dedup gate (ConcurrentHashMap)
  - CloudRunJobsClient.executeJob()
  - Dispatches job via Cloud Run Jobs Admin API
  - Polls LRO every 30s until done
  - Releases dedup key on completion
     │
     ▼
Cloud Run Job Execution (ephemeral container)
  - Clones repo via HTTPS token
  - Writes .mcp.json (Atlassian MCP)
  - Runs: claude -p "$PROMPT" --dangerously-skip-permissions
  - Opens PR, exits
```

## Key Files

- `agent/service/JobDispatcher.kt` — interface with single `executeJob` method; the seam for future intelligence (MS-179)
- `agent/service/CloudRunJobsClient.kt` — dispatches job, polls LRO until `done: true`
- `agent/service/AgentLaunchService.kt` — branches on `dispatcher != null`; Cloud Run path skips local worktrees
- `agent/di/AgentConfig.kt` — `useCloudRunWorkers`, `gcpProjectId`, `gcpRegion`, `gcpJobName`, `googleCredentialsJson`
- `agent/di/AgentModule.kt` — builds `CloudRunJobsClient` only when config is present; null means local mode
- `Dockerfile.worker` — eclipse-temurin:21 base, installs Node.js, gh CLI, mcp-atlassian, claude-code
- `agent/worker-entrypoint.sh` — clones repo, writes `.mcp.json`, execs `claude -p "$PROMPT"`

## Why Cloud Run Jobs

Cloud Run Jobs is the idiomatic GCP primitive for this pattern: ephemeral, stateless, pay-per-execution, no infrastructure to manage. Each agent run gets a clean container — no shared state, no leftover git state between tickets.

Alternative considered: Cloud Run Services (always-on). Rejected because agents are long-running, low-frequency tasks. Paying for an idle service would cost more than per-execution billing.

## LRO Polling

The Cloud Run Jobs `run` API returns a long-running operation immediately. The worker may run for 5–15 minutes. Without polling:
- The dispatch HTTP call returns in ~1 second
- The dedup key is released immediately
- Any Jira webhook firing during the worker's run dispatches a duplicate worker

Fix: `CloudRunJobsClient.pollUntilDone()` polls `GET https://run.googleapis.com/v2/{operationName}` every 30 seconds until `done: true`. This is the standard GCP LRO pattern — Google's own client libraries do the same. The dedup key stays held for the full duration.

## Worker Env Vars

The orchestrator injects all required env vars per execution via `containerOverrides`:

| Var | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Claude Code API auth |
| `ANTHROPIC_AUTH_TOKEN` | Enterprise OAuth token |
| `ANTHROPIC_BASE_URL` | Enterprise API endpoint |
| `ANTHROPIC_MODEL` | Model selection |
| `GITHUB_BOT_TOKEN` | Repo clone + gh CLI |
| `GITHUB_BOT_NAME` / `EMAIL` / `LOGIN` | Git identity |
| `JIRA_EMAIL` / `JIRA_API_TOKEN` | MCP Atlassian |
| `PROMPT` | Per-execution bootstrap prompt |
| `TICKET_KEY` | Ticket identifier |

## Resource Sizing

Cloud Run Job is configured with **4 GiB memory, 2 vCPUs, 1 task, 0 retries**. Gradle requires ~2–3 GiB for a cold build (no daemon cache in ephemeral containers). 2 vCPUs noticeably cuts Kotlin compilation time vs 1.

Retries set to 0: the dedup gate and Jira webhook handle re-triggering. Cloud Run automatic retries would bypass the gate and duplicate work.

## Smoke Test File

`agent/src/main/kotlin/com/mediasage/agent/SmokeTest.kt` exists as a dedicated throwaway target for validating the end-to-end worker path. Bump the version number and open a PR — no Gradle build required, fast feedback loop.

## Lessons Learned

- **SA key org policy**: GCP org policy `iam.disableServiceAccountKeyCreation` blocks key generation by default. Requires Organization Administrator role at the org level to disable — project-level IAM is not sufficient.
- **Docker platform**: Always build worker images with `--platform linux/amd64`. Cloud Run rejects arm64 images (Apple Silicon default) with a cryptic deployment error.
- **Base64 whitespace**: Pipe SA key through `tr -d '[:space:]'` before base64 encoding for env vars. A single space character causes `Illegal base64 character 20` at runtime.
- **`open` class anti-pattern**: Making classes `open` solely for testability is non-idiomatic Kotlin. Use interfaces instead — `JobDispatcher` is the correct pattern. Tracked in MS-180.
