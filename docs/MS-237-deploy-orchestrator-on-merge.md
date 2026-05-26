# MS-237 — Automate Orchestrator Image Build and Deploy on Merge to Main

## What Was Built

A GitHub Actions workflow (`.github/workflows/deploy-orchestrator.yml`) that automatically
builds, pushes, and deploys the orchestrator image whenever `agent/` files change on `main`.

Before this, every orchestrator change required a manual `docker build --platform linux/amd64
... && docker push ... && gcloud run services update ...` sequence before the new code was live
on Railway → Cloud Run Service.

## Trigger Paths

The workflow fires on push to `main` only when one of these paths changes:

| Path | Why |
|---|---|
| `agent/**` | Any orchestrator source, config, or Dockerfile change |
| `settings-agent.gradle.kts` | Scoped build file — affects what gets compiled into the image |

Docs-only, server, shared, or composeApp changes do not trigger the workflow.

## Steps

1. **Log in to Artifact Registry** — `docker/login-action` with `_json_key` + `GCP_CREDENTIALS` secret
2. **Set up Docker Buildx** — required by `build-push-action` for BuildKit support
3. **Build and push** — `agent/Dockerfile`, `linux/amd64`, tagged `orchestrator:latest`
4. **Authenticate to GCP** — `google-github-actions/auth@v2` with the same `GCP_CREDENTIALS` secret
5. **Deploy to Cloud Run** — `google-github-actions/deploy-cloudrun@v2` updates `media-sage-orchestrator` to the new image revision

## Authentication

Two GCP auth steps in one workflow:

- **Docker push**: uses `docker/login-action` with `_json_key` — no gcloud needed, Docker's credential helper handles Artifact Registry directly
- **Cloud Run deploy**: uses `google-github-actions/auth@v2` — sets up ADC for the `deploy-cloudrun` action

Both use the same `GCP_CREDENTIALS` secret (GCP service account JSON key). No new secrets required beyond what `build-worker-image.yml` already uses.

**Required IAM roles** on the service account:
- `roles/artifactregistry.writer` — to push images
- `roles/run.developer` — to update the Cloud Run Service revision

## Key Decisions

**`google-github-actions/deploy-cloudrun@v2` over raw `gcloud`** — avoids installing the gcloud
CLI as a separate step, handles authentication via ADC automatically, and keeps the workflow
declarative rather than imperative.

**`cancel-in-progress: true`** — if two merges land in quick succession, the first in-flight
build is cancelled. Only the latest image matters; queuing stale builds wastes CI minutes and
can deploy an older revision second.

**Same `GCP_CREDENTIALS` secret as the worker workflow** — the orchestrator and worker share
the same service account. No additional setup required.

## Relationship to MS-196

MS-196 automated the worker image build. MS-237 does the same for the orchestrator and adds
the Cloud Run deploy step. Together they mean no manual intervention is required after any
merge — both images stay current automatically.
