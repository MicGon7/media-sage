# MS-196 — GitHub Actions Workflow to Auto-Rebuild Worker Image on Merge to Main

## What Was Built

A GitHub Actions workflow (`.github/workflows/build-worker-image.yml`) that automatically
rebuilds and pushes the Cloud Run worker image whenever relevant files change on `main`.

Before this, every change to `Dockerfile.worker` or its dependencies required a manual
`docker build --platform linux/amd64 ... && docker push ...` before the new image was live.

## Trigger Paths

The workflow fires on push to `main` only when one of these paths changes:

| Path | Why |
|---|---|
| `Dockerfile.worker` | Direct image definition |
| `agent/worker-entrypoint.sh` | Worker startup script baked into image |
| `gradlew` / `gradle/wrapper/**` | Gradle wrapper — determines Gradle version |
| `gradle/libs.versions.toml` | Dependency versions — pre-baked deps may change |
| `settings.gradle.kts` | Module structure — affects `--configure-on-demand` scope |
| `build.gradle.kts` | Root build configuration |
| `agent/build.gradle.kts` | Agent dependencies — pre-baked into image |
| `server/build.gradle.kts` | Server dependencies — pre-baked into image |

The workflow does NOT run on pull requests — only on merge to `main`. This avoids
wasting Artifact Registry pushes on unreviewed branches.

## Authentication

Uses `docker/login-action` with `_json_key` and `GCP_CREDENTIALS` GitHub secret
(a GCP service account JSON key). No gcloud installation required — Docker's credential
helper handles Artifact Registry auth directly.

**Required IAM role:** The service account in `GCP_CREDENTIALS` needs
`roles/artifactregistry.writer` on the `media-sage-agent` project (or scoped to the
`media-sage-agent` Artifact Registry repository).

## Setup (one-time, manual)

1. Identify or create a GCP service account with `roles/artifactregistry.writer`
2. Generate a JSON key for that service account
3. In the GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `GCP_CREDENTIALS`
   - Value: the full JSON key contents

## Build Configuration

- `--platform linux/amd64` via `docker/build-push-action` — Cloud Run rejects multi-arch
  OCI manifest lists, so the image must target `linux/amd64` explicitly even when built
  on an `ubuntu-latest` runner (which is already `amd64`, but `docker buildx` defaults
  to the manifest list format without an explicit platform)
- `docker/setup-buildx-action` enables BuildKit, which is required by `build-push-action`
- `concurrency` group cancels in-flight builds if a second merge lands before the first
  completes — only the latest image matters

## Key Learning

The `agent/build.gradle.kts` and `server/build.gradle.kts` paths are included in the
trigger because MS-209 pre-bakes `:agent` and `:server` dependencies into the image at
build time. If a new dependency is added to either module, the image must be rebuilt so
the new JAR is baked in — otherwise workers download it at runtime on cold starts.
