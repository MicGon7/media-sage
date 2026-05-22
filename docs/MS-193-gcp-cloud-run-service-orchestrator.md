# MS-193: Deploy Agent Orchestrator to GCP Cloud Run Service

## What Was Built

Migrated the `:agent` orchestration server from a Railway Docker container to a **GCP Cloud Run Service** (`media-sage-orchestrator`). The service receives Jira and GitHub webhooks and dispatches Cloud Run Job workers — the same architecture, now running natively on GCP alongside the workers it manages.

## Why GCP Over Railway

| Concern | Railway | GCP Cloud Run Service |
|---|---|---|
| SLA | None | 99.95% uptime SLA |
| Platform risk | Runs on GCP; Railway's GCP account was suspended in 2025, causing a platform-wide outage | First-party GCP — no intermediary |
| Scale | Good for prototypes | Scales to enterprise; no ceiling |
| Observability | Per-service logs only | Cloud Logging: orchestrator + worker logs in a single pane of glass |
| VPC/networking | Limited | Full VPC integration — services communicate privately |
| KMP/JVM support | Works | Works |

Railway is kept as a manual fallback — deactivated, env vars intact. Switching takes ~2 minutes (redeploy + update two webhook URLs).

## Architecture

```
Jira webhook ──────────────────────────────────────────────┐
GitHub webhook ─────────────────────────────────────────────▶  media-sage-orchestrator
                                                              (Cloud Run Service, min=1)
                                                                        │
                                                             dispatches Cloud Run Job
                                                                        │
                                                              media-sage-agent-worker
                                                              (Cloud Run Job, per-ticket)
                                                                        │
                                                             Pub/Sub → /webhook/pubsub
                                                             (job completion signal back
                                                              to orchestrator)
```

## Key Decisions

### Cloud Run Service vs Job
- **Service**: long-running HTTP server, always-on, responds to webhooks. Used for the orchestrator.
- **Job**: finite task, runs to completion, no inbound HTTP. Used for Claude Code workers.

### Secret Manager vs Plain Env Vars
Secrets are stored in GCP Secret Manager; the orchestrator SA has `secretAccessor` on each. Plain env vars (non-sensitive config like emails, project IDs, region names) are set directly on the service.

Rule of thumb: if it grants access or costs money if leaked → Secret Manager. Everything else → plain env var.

### `--min-instances=1`
The orchestrator must be always-on to receive webhooks. Without `--min-instances=1`, Cloud Run scales to zero and a cold start on webhook arrival can cause Jira/GitHub to time out and mark the delivery as failed.

### Trailing Newlines in Secrets
`echo "$VALUE" | gcloud secrets create` appends a trailing newline. Git URL parsing rejects newlines in credentials, causing a `fatal: credential url cannot be parsed` error. Fix: use Python subprocess with `.strip()` when piping secret values, or `printf '%s'` instead of `echo`.

### `PORT` is Reserved by Cloud Run
Cloud Run injects `PORT` automatically. Setting it in `--set-env-vars` raises an error. The app's `application.conf` uses `${?PORT}` — it picks up the injected value transparently, no code change needed.

### `linux/amd64` Platform
Cloud Run requires AMD64 images. Apple Silicon builds ARM64 by default. Always build with:
```bash
docker buildx build --platform linux/amd64 ...
```

## Deployment Commands

### Build and push image
```bash
docker buildx build --platform linux/amd64 \
  -t us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/orchestrator:latest \
  -f agent/Dockerfile \
  --push .
```

### Deploy (initial)
```bash
gcloud run deploy media-sage-orchestrator \
  --image us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/orchestrator:latest \
  --region us-central1 \
  --project media-sage-agent \
  --service-account media-sage-orchestrator@media-sage-agent.iam.gserviceaccount.com \
  --port 8081 \
  --min-instances 1 \
  --max-instances 3 \
  --memory 2Gi \
  --cpu 1 \
  --timeout 3600 \
  --allow-unauthenticated \
  --set-secrets="ANTHROPIC_AUTH_TOKEN=anthropic-auth-token:latest,GITHUB_BOT_TOKEN=github-bot-token:latest,..." \
  --set-env-vars="AGENT_REPO_PATH=/home/agent/media-sage,..."
```

### Redeploy after image update
```bash
gcloud run deploy media-sage-orchestrator \
  --image us-central1-docker.pkg.dev/media-sage-agent/media-sage-agent/orchestrator:latest \
  --region us-central1 --project media-sage-agent
```

### Health check
```bash
curl https://media-sage-orchestrator-924166357877.us-central1.run.app/health
# → OK
```

## Service Account IAM

`media-sage-orchestrator` SA needs:
- `roles/secretmanager.secretAccessor` on each secret it mounts
- (Worker dispatch uses a separate `GOOGLE_CREDENTIALS_BASE64` SA JSON key, not the orchestrator SA itself)

To grant a shared secret to both orchestrator and worker SAs:
```bash
gcloud secrets add-iam-policy-binding SECRET_NAME \
  --member="serviceAccount:SA@PROJECT.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor" \
  --project=media-sage-agent
```

## Observability

Cloud Logging aggregates orchestrator + worker logs under `media-sage-agent` project. No per-instance clicking — filter by `resource.type="cloud_run_revision"` or `resource.type="cloud_run_job"` to distinguish them.

## Smoke Test

1. Assign a Jira ticket to `media-sage-bot`, set status → In Progress
2. Watch Cloud Logging: orchestrator receives webhook → inserts job → dispatches worker
3. Worker runs Claude Code, pushes branch, opens PR
4. Pub/Sub fires `/webhook/pubsub` → orchestrator marks job COMPLETED

## What Didn't Change

- All application code (routes, services, DI) is unchanged
- Worker image (`Dockerfile.worker`) is unchanged
- Supabase job registry schema is unchanged
- The Railway API server (`:server`) remains on Railway at port 8080 — unaffected
