# agent — Orchestrator Server

## Structure

```
agent/src/main/kotlin/com/mediasage/agent/
├── Application.kt       — Entry point, Koin setup (port 8081)
├── di/                  — AgentConfig, AgentModule
├── db/                  — AgentDatabase, JobsTable, JobRepository (Supabase Postgres)
├── plugins/             — ContentNegotiation, CallLogging, StatusPages
├── routes/              — JiraWebhookRoutes, GitHubWebhookRoutes
├── service/             — AgentLaunchService, BriefingService, CloudRunDispatch, CloudRunJobsClient, JiraApiService
└── tools/               — ToolDefinitions (Anthropic orchestrator-worker pattern)
```

## Dependency Injection

`agentModule(config, scope)` wires HttpClient, AgentLaunchService, BriefingService, JiraApiService, and CloudRunJobsClient via Koin. Define modules per feature, not per layer.

**Interface bindings in tests:** When a route resolves a type via `inject<SomeInterface>()`, every test Koin module that exercises that route must include `single<SomeInterface> { get<ConcreteImpl>() }`. Missing this binding causes the inject to fail at the call site — not at startup — so tests that never reach the inject (e.g. early-return paths) pass silently while tests that do reach it return 500 instead of the expected status. After introducing a new interface in `AgentModule`, search all `*RouteTest.kt` files for manual Koin `module { }` blocks and add the interface binding to each.

## Job Registry (Supabase Postgres)

The orchestrator maintains a persistent `jobs` table in Supabase Postgres. This replaces the in-memory dedup gate and survives restarts.

**Schema:**
```sql
CREATE TABLE jobs (
  job_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_key     TEXT NOT NULL,
  prompt         TEXT NOT NULL,
  status         TEXT NOT NULL DEFAULT 'PENDING',
  execution_name TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ
);
CREATE INDEX ON jobs (ticket_key, created_at DESC);
```

**Job status state machine:** `PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`

**Dedup logic:** Before dispatching, query the latest row for `ticket_key`:
- `RUNNING` → skip (concurrent duplicate)
- `COMPLETED` → skip (already done, permanent dedup)
- `FAILED` / `INTERRUPTED` → re-dispatch (retry eligible)
- No row → dispatch fresh

**Recovery on startup:** `AgentLaunchService.recoverInterruptedJobs()` queries all RUNNING rows. For each, `CloudRunJobsClient.recoverJob()` makes a real Cloud Run API call using the saved execution name — if the execution is still running, no-op (Pub/Sub will signal completion); if the execution is gone (404), marks the job INTERRUPTED and posts a Jira comment instructing the team to re-trigger manually.

## Cloud Run Dispatch

**Env var overrides append, not replace.** When dispatching a Cloud Run Job with per-run env var overrides (`containerOverrides.env`), the values are appended to the job's existing env vars — they do NOT replace them. If the same key exists in both the static job definition and the per-run override, the static value takes precedence. Rule: never set per-target or per-run values as static env vars on the job definition. Inject them exclusively at dispatch time via `DispatchConfig`. The job definition should only hold env vars that are truly static across all runs (e.g. `ANTHROPIC_BASE_URL`, `GCP_PROJECT_ID`).

## Deployment (Container — Production)

The `:agent` server runs as a GCP Cloud Run Service (`media-sage-orchestrator`). It clones the repo at startup using the bot account token, then starts the Ktor server.

- **Service:** `media-sage-orchestrator`
- **URL:** `https://media-sage-orchestrator-924166357877.us-central1.run.app`
- **Project:** `media-sage-agent` · **Region:** `us-central1`
- **Service account:** `media-sage-orchestrator@media-sage-agent.iam.gserviceaccount.com`
- **Config:** `--min-instances=1 --max-instances=3 --memory=2Gi --cpu=1 --port=8081 --timeout=3600`
- **Image:** `us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/orchestrator:latest` (linux/amd64)

**Plain env vars (non-sensitive):**

| Variable | Value |
|---|---|
| `AGENT_REPO_PATH` | `/home/agent/media-sage` |
| `GITHUB_BOT_LOGIN` | `media-sage-worker[bot]` (GitHub App identity — note `[bot]` suffix) |
| `GITHUB_BOT_NAME` | `media-sage-worker` |
| `GITHUB_APP_ID` | Numeric App ID from the `media-sage-worker` GitHub App settings page |
| `GITHUB_APP_INSTALLATION_ID` | Installation ID for the media-sage repo |
| `JIRA_EMAIL` | `micgon7@gmail.com` |
| `JIRA_BOT_EMAIL` | Bot Jira account email |
| `JIRA_CLOUD_ID` | `ad358528-f7e9-4e40-9531-c51049908d6d` |
| `JIRA_BOT_ACCOUNT_ID` | Jira account ID of the bot user |
| `GCP_PROJECT_ID` | `media-sage-agent` |
| `GCP_REGION` | `us-central1` |
| `GCP_JOB_NAME` | `media-sage-agent-worker` |
| `ANTHROPIC_BASE_URL` | `https://api.fuelix.ai` (Fuelix proxy) |

**Secrets (Secret Manager):**

| Secret name | Env var | Description |
|---|---|---|
| `anthropic-auth-token` | `ANTHROPIC_AUTH_TOKEN` | Fuelix API token (`ak-...`) |
| `github-app-private-key-base64` | `GITHUB_APP_PRIVATE_KEY_BASE64` | RSA private key, base64-encoded PEM |
| `github-webhook-secret` | `GITHUB_WEBHOOK_SECRET` | Shared secret for GitHub webhook HMAC verification |
| `jira-api-token` | `JIRA_API_TOKEN` | Atlassian account API token |
| `jira-bot-api-token` | `JIRA_BOT_API_TOKEN` | Bot Atlassian API token |
| `supabase-db-url` | `SUPABASE_DB_URL` | Postgres URI with credentials |
| `pubsub-webhook-secret` | `PUBSUB_WEBHOOK_SECRET` | Shared secret for Pub/Sub push URL auth |
| `google-credentials-base64` | `GOOGLE_CREDENTIALS_BASE64` | Base64-encoded GCP SA JSON (worker dispatch) |

**GitHub App auth pattern:** Both orchestrator and worker authenticate as `media-sage-worker[bot]` using short-lived installation tokens (1-hour TTL). Tokens are generated at container startup via `get-github-token.py` (JWT → GitHub API exchange) and exported as `GH_TOKEN`. The git commit email is derived automatically from the App ID: `{GITHUB_APP_ID}+media-sage-worker[bot]@users.noreply.github.com`. Store the private key base64-encoded: `base64 -i private-key.pem | tr -d '\n'`.

**To redeploy** after a new image push:
```bash
gcloud run deploy media-sage-orchestrator \
  --image us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/orchestrator:latest \
  --region us-central1 --project media-sage-agent
```

**Manual fallback (Railway):** The Railway `:orchestrator` service retains all env vars and is kept deactivated. To switch back: redeploy Railway service → update Jira + GitHub webhook URLs to the Railway URL.

## Local Dev

1. Add `export AGENT_REPO_PATH="/path/to/media-sage"` to `~/.zshrc` and `source ~/.zshrc`
2. Start the agent server: `source ~/.zshrc && ./gradlew :agent:run`
3. Start ngrok: `ngrok http 8081` — copy the public HTTPS URL
4. Temporarily update Jira and GitHub webhook URLs to the ngrok URL

## Webhook URLs

- Jira: `https://media-sage-orchestrator-924166357877.us-central1.run.app/webhook/jira`
- GitHub: `https://media-sage-orchestrator-924166357877.us-central1.run.app/webhook/github`

Register the Jira webhook at **media-sage.atlassian.net → Settings → System → WebHooks**:
- Events: Issue **created** and **updated**
- JQL filter: `project = MS` (assignee + status filtering is done in the route, not here)

Register the GitHub webhook in repo **Settings → Webhooks**:
- Content type: `application/json`
- Events: `Pull request reviews`, `Pull request review comments`

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
