# MS-188: Move Cloud Run Worker Secrets to GCP Secret Manager

## What was built

Cloud Run job executions were exposing secrets (`ANTHROPIC_AUTH_TOKEN`, `GITHUB_BOT_TOKEN`, `JIRA_API_TOKEN`) as plaintext in the execution spec — visible to anyone with access to `gcloud run jobs executions list` or the Cloud Console. This ticket migrated those secrets to GCP Secret Manager and updated the job template to inject them via `secretKeyRef` at execution time.

## What changed

**GCP (via gcloud CLI):**

1. Created three secrets in Secret Manager (`anthropic-auth-token`, `github-bot-token`, `jira-api-token`) and loaded each secret version.
2. Granted the worker service account (`media-sage-orchestrator@media-sage-agent.iam.gserviceaccount.com`) the `roles/secretmanager.secretAccessor` IAM role on each secret.
3. Updated the Cloud Run job template to reference secrets via `--set-secrets` — Cloud Run now injects the values at execution time from Secret Manager.
4. Set all static non-secret env vars (`ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL`, `GITHUB_BOT_LOGIN`, etc.) in the job template via `--env-vars-file`, so they are no longer sent as per-execution overrides.

**Kotlin (`CloudRunJobsClient.kt`, `AgentModule.kt`):**

- Removed the `agentEnvVars: Map<String, String>` parameter from `CloudRunJobsClient` — no longer needed since all static config and secrets live in the job template.
- The per-execution override payload now contains only `PROMPT` and `TICKET_KEY` — the two values that are genuinely dynamic per job.

## The idiomatic pattern

| Category | Where it lives | How |
|---|---|---|
| Secrets | GCP Secret Manager → job template | `--set-secrets=KEY=secret-name:latest` |
| Static config | Job template as plain env vars | `--env-vars-file=vars.yaml` |
| Per-execution dynamic values | Execution overrides only | `containerOverrides.env` in the run request |

## Why `--env-vars-file` for static config

`--set-env-vars` with a comma-separated list breaks when values contain `:` (e.g. a URL like `https://api.fuelix.ai`). The `^:^` alternate-separator trick also fails when `:` appears in values. The `--env-vars-file` flag accepts a YAML file (`KEY: value`) and sidesteps all escaping issues entirely — the right tool whenever values contain special characters.

## Enabling Secret Manager

The Secret Manager API is not enabled by default on a GCP project. The first `gcloud secrets create` command prompts to enable it — answering `y` enables the API and retries automatically.
