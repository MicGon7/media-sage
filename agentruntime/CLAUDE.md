# agentruntime — Orchestrator Server

## Structure

```
agentruntime/src/main/kotlin/com/mediasage/agentruntime/
├── Application.kt       — Entry point, Koin setup (port 8081)
├── di/                  — AgentConfig, AgentModule
├── db/                  — AgentDatabase, JobsTable, JobRepository (Supabase Postgres)
├── evaluation/          — AgentService, ClaudeAgentService, NoOpAgentService
├── feedback/            — GitHubApiClient, GitHubAppClient
├── plugins/             — ContentNegotiation, CallLogging, StatusPages
├── routes/              — JiraWebhookRoutes, GitHubWebhookRoutes, PubSubWebhookRoutes
└── service/             — AgentLaunchService, CloudRunDispatch, CloudRunJobsClient, JiraApiClient,
                            SlackApiClient, JobCompletionNotifier
```

## Prompts

System prompts must never be hardcoded in Kotlin. Define them in `src/main/resources/prompts/` and load at runtime via classpath. Plain language only — avoid technical AI jargon in file and variable names so anyone maintaining the code can follow them.

## Dependency Injection

`agentModule(config, scope)` wires HttpClient, AgentLaunchService, JiraApiClient, and CloudRunJobsClient via Koin. Define modules per feature, not per layer.

**Interface bindings in tests:** When a route resolves a type via `inject<SomeInterface>()`, every test Koin module that exercises that route must include `single<SomeInterface> { get<ConcreteImpl>() }`. Missing this binding causes the inject to fail at the call site — not at startup — so tests that never reach the inject (e.g. early-return paths) pass silently while tests that do reach it return 500 instead of the expected status. After introducing a new interface in `AgentModule`, search all `*RouteTest.kt` files for manual Koin `module { }` blocks and add the interface binding to each.

## Job Registry (Supabase Postgres)

The orchestrator maintains a persistent `jobs` table in Supabase Postgres. This replaces the in-memory dedup gate and survives restarts.

**Schema:**
```sql
CREATE TABLE jobs (
  job_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_key     TEXT NOT NULL,
  payload        TEXT NOT NULL,
  status         TEXT NOT NULL DEFAULT 'PENDING',
  execution_name TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ
);
CREATE INDEX ON jobs (ticket_key, created_at DESC);
```

`payload` stores a compact JSON object of the identifiers dispatched to the worker
(e.g. `{"ticketKey":"MS-123"}` for ticket-work, `{"prNumber":"456"}` for PR jobs).
The `prompt` column was renamed to `payload`; the migration runs idempotently
in `AgentDatabase.migrate()`.

Additional nullable columns are added via idempotent migrations in `AgentDatabase.migrate()`
(never in the base `CREATE TABLE`): worker efficiency metrics (MS-210 — tokens, cost, duration,
`num_turns`) and model tracking (MS-386 — `model_version`, parsed from the `result` event's
`modelUsage` key alongside the other metrics). MS-386's sibling `failed_gate` column was retired
in MS-576 (idempotent `DROP COLUMN IF EXISTS` in `migrate()`) — run death (`status = FAILED`) is
not a gate failure, and the hardened pipeline suppresses gate failures by design, so it was never
populated. See `docs/MS-386-jobs-failure-attribution-model.md`.

**Job status state machine:** `PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`

**Dedup logic:** Before dispatching, query the latest row for `ticket_key`:
- `RUNNING` → skip (concurrent duplicate)
- `COMPLETED` → skip (already done, permanent dedup)
- `FAILED` / `INTERRUPTED` → re-dispatch (retry eligible)
- No row → dispatch fresh

**Recovery on startup:** `AgentLaunchService.recoverInterruptedJobs()` queries all RUNNING rows. For each, `CloudRunJobsClient.recoverJob()` makes a real Cloud Run API call using the saved execution name — if the execution is still running, no-op (Pub/Sub will signal completion); if the execution is gone (404), marks the job INTERRUPTED and posts a Jira comment instructing the team to re-trigger manually.

## Cloud Run Dispatch

**Env var overrides append, not replace.** When dispatching a Cloud Run Job with per-run env var overrides (`containerOverrides.env`), the values are appended to the job's existing env vars — they do NOT replace them. If the same key exists in both the static job definition and the per-run override, the static value takes precedence. Rule: never set per-target or per-run values as static env vars on the job definition. Inject them exclusively at dispatch time via `DispatchConfig`. The job definition should only hold env vars that are truly static across all runs (e.g. `ANTHROPIC_BASE_URL`, `GCP_PROJECT_ID`).

**Dispatch model:** The orchestrator is a pure dispatcher — it passes only the minimum job identifiers as env vars. No prompt strings are constructed in the orchestrator. Per-run env vars at dispatch time:

| Job type | Env vars passed |
|---|---|
| `ticket-work` | `JOB_TYPE`, `JOB_ID`, `TICKET_KEY` |
| `pr-review-work` | `JOB_TYPE`, `JOB_ID`, `PR_NUMBER` |
| `conflict-resolution-work` | `JOB_TYPE`, `JOB_ID`, `PR_NUMBER` |
| `pr-quality-work` | `JOB_TYPE`, `JOB_ID`, `PR_NUMBER`, `TICKET_KEY` (=`QUALITY-{n}`), `JIRA_TICKET_KEY` |

The worker entrypoint runs `claude -p "/$JOB_TYPE"` — the skill is the entry point and owns all framing and context fetching.

AC compliance evaluation (`judge-work`) is no longer a Cloud Run Job. It runs inline inside the orchestrator as `ClaudeAgentService` after a successful ticket-work Pub/Sub completion event.

**Post-PR reviews (both fire on a successful `ticket-work` completion, in parallel, advisory-only):**
- **AC compliance judge** — inline `ClaudeAgentService`, diff-only, single API call.
- **Code-quality review** (`pr-quality-work`) — a Cloud Run Job with the repo cloned, dispatched via
  `AgentLaunchService.launchForQualityReview`. Reviews for idiom/reuse and challenges whether repo
  conventions are correct and code is reachable — the failure mode the diff-only judge can't see.
  Dedup key `QUALITY-{prNumber}` (distinct from `pr-review-work`'s `PR-{n}` so neither suppresses the
  other). It posts one GitHub review with state `COMMENT` (never `REQUEST_CHANGES`) prefixed
  `🤖 **Agent:**`, so it can't trigger `pr-review-work`. `JIRA_TICKET_KEY` is set on its completion
  event so the `jiraTicketKey == null` recursion guard excludes it from re-judging/re-dispatch.

## Deployment (Container — Production)

The `:agentruntime` server runs as a GCP Cloud Run Service (`media-sage-orchestrator`). It is a stateless HTTP server — it receives webhooks, builds prompts, and dispatches Cloud Run Jobs. It does not clone any repo.

**The deploy is declarative — `.github/workflows/deploy-orchestrator.yml` is the source of truth** for the service account, env vars, secrets, scaling, and resources. It uses `overwrite` update strategies, so any env var or secret not listed in the workflow is removed on the next deploy. The tables below mirror the workflow; keep them in sync. Only vars the app actually consumes (see `src/main/resources/application.conf`) are declared — historical drift (`AGENT_REPO_PATH`, `ATLASSIAN_EMAIL`, `GITHUB_BOT_EMAIL`, `GITHUB_BOT_NAME`, `GCP_JUDGE_JOB_NAME`, `GCP_COMMENT_JOB_NAME`, and the `atlassian-api-token` / `github-bot-token` secrets) was pruned in MS-392.

- **Service:** `media-sage-orchestrator`
- **URL:** `https://media-sage-orchestrator-924166357877.us-central1.run.app`
- **Project:** `media-sage-agent` · **Region:** `us-central1`
- **Service account:** `media-sage-orchestrator@media-sage-agent.iam.gserviceaccount.com`
- **Config:** `--min-instances=1 --max-instances=3 --memory=2Gi --cpu=1 --port=8081 --timeout=3600`
- **Image:** `us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/orchestrator:latest` (linux/amd64)

**Plain env vars (non-sensitive):**

| Variable | Value |
|---|---|
| `GITHUB_BOT_LOGIN` | `media-sage-worker[bot]` (GitHub App identity — note `[bot]` suffix) |
| `GITHUB_APP_ID` | `3848870` (AC judge — reads PR details/diffs) |
| `GITHUB_APP_INSTALLATION_ID` | `135953548` (AC judge — reads PR details/diffs) |
| `GITHUB_OWNER` | `michael-gonzalez-dev` |
| `GITHUB_REPO` | `media-sage` |
| `JIRA_EMAIL` | `micgon7@gmail.com` |
| `JIRA_BOT_EMAIL` | Bot Jira account email |
| `JIRA_BOT_ACCOUNT_ID` | Jira account ID of the bot user |
| `GCP_PROJECT_ID` | `media-sage-agent` |
| `GCP_REGION` | `us-central1` |
| `ANTHROPIC_BASE_URL` | `https://api.fuelix.ai` (Fuelix proxy) |
| `ANTHROPIC_MODEL` | Support-service (AC judge) model. Set as a literal in `deploy-orchestrator.yml`; falls back to pipelineCore's `DEFAULT_CLAUDE_MODEL` when unset (local dev). Fuelix requires the short-form alias (e.g. `claude-sonnet-5`). |

> `JIRA_CLOUD_ID` and `GCP_JOB_NAME` are **not** set — the app falls back to the correct hardcoded defaults in `application.conf` (`ad358528-…` and `media-sage-agent-worker`), so the declarative deploy leaves them unset to match production.

**Secrets (Secret Manager):**

| Secret name | Env var | Description |
|---|---|---|
| `anthropic-auth-token` | `ANTHROPIC_AUTH_TOKEN` | Fuelix API token (`ak-...`) |
| `github-webhook-secret` | `GITHUB_WEBHOOK_SECRET` | Shared secret for GitHub webhook HMAC verification |
| `jira-api-token` | `JIRA_API_TOKEN` | Atlassian account API token |
| `jira-bot-api-token` | `JIRA_BOT_API_TOKEN` | Bot Atlassian API token |
| `supabase-db-url` | `SUPABASE_DB_URL` | Postgres URI with credentials |
| `pubsub-webhook-secret` | `PUBSUB_WEBHOOK_SECRET` | Shared secret for Pub/Sub push URL auth |
| `google-credentials-base64` | `GOOGLE_CREDENTIALS_BASE64` | Base64-encoded GCP SA JSON (worker dispatch) |
| `github-app-private-key-base64` | `GITHUB_APP_PRIVATE_KEY_BASE64` | Base64-encoded GitHub App RSA key (AC judge PR reads) |
| `slack-webhook-url` | `SLACK_WEBHOOK_URL` | Slack incoming-webhook URL for job-completion notifications (blank disables) |

> All secrets except `github-app-private-key-base64` are prefixed `orchestrator-` in Secret Manager (e.g. `orchestrator-anthropic-auth-token`); the workflow references the full names.

**GitHub App auth pattern:** Workers authenticate as `media-sage-worker[bot]` using short-lived installation tokens (1-hour TTL) generated at job startup. The orchestrator also uses GitHub App auth for one narrow purpose: the inline AC-compliance judge (`ClaudeAgentService`) reads PR details/diffs via `GitHubAppClient`. It makes no GitHub *write* calls. The git commit email for workers is derived automatically from the App ID: `{GITHUB_APP_ID}+media-sage-worker[bot]@users.noreply.github.com`.

**To redeploy:** push to `main` with changes under `agentruntime/**` — `deploy-orchestrator.yml` builds the image and deploys the service declaratively (SA, env vars, secrets, scaling, resources). No manual `gcloud run deploy` is needed; running one by hand risks re-introducing the out-of-band drift this workflow exists to prevent. Trigger a config-only redeploy via the workflow's **Run workflow** button or an empty commit under `agentruntime/`.

**Manual fallback (Railway):** The Railway `media-sage-orchestrator` service retains all env vars and is kept deactivated. To switch back: redeploy Railway service → update Jira + GitHub webhook URLs to the Railway URL.

## Local Dev

1. Start the agentruntime server: `source ~/.zshrc && ./gradlew :agentruntime:run`
2. Start ngrok: `ngrok http 8081` — copy the public HTTPS URL
3. Temporarily update Jira and GitHub webhook URLs to the ngrok URL

## Webhook URLs

- Jira: `https://media-sage-orchestrator-924166357877.us-central1.run.app/webhook/jira`
- GitHub: `https://media-sage-orchestrator-924166357877.us-central1.run.app/webhook/github`

Register the Jira webhook at **media-sage.atlassian.net → Settings → System → WebHooks**:
- Events: Issue **created** and **updated**
- JQL filter: `project = MS` (assignee + status filtering is done in the route, not here)

Register the GitHub webhook in repo **Settings → Webhooks**:
- Content type: `application/json`
- Events: `Pull request reviews`

## Cloud Run Logs Explorer Queries

Use these in GCP Logs Explorer for clean demo log views:

**Worker (Cloud Run Job):**
```
resource.type="cloud_run_job"
resource.labels.job_name="media-sage-agent-worker"
textPayload=~"."
```

**Orchestrator (Cloud Run Service):**
```
resource.type="cloud_run_revision"
resource.labels.service_name="media-sage-orchestrator"
textPayload=~"."
```

See `docs/MS-193-gcp-cloud-run-service-orchestrator.md` for full GCP migration notes.
See `docs/diagrams/agent-pipeline.md` for the full autonomous pipeline flow diagram.
See `docs/diagrams/infrastructure-overview.md` for the infrastructure architecture diagram.
