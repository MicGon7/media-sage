# MS-204 — Persist Gradle Cache Across Cloud Run Worker Executions via GCS Volume

> **Superseded by MS-209.** The GCS FUSE approach described here was removed because
> GCS FUSE does not support the POSIX file locking that Gradle's `modules-2` cache
> requires on cold starts. Warm-cache reads worked; cold-start writes silently failed,
> causing plugin resolution errors. MS-209 replaced this with dependency pre-baking in
> the Docker image layer. The GCS bucket `media-sage-gradle-cache` and its Cloud Run
> volume mount have been removed.

## What was built

Every Cloud Run worker execution previously started cold — no compiled classes,
no cached configuration. This cost 3-5 minutes of Gradle compilation on every
run regardless of whether dependencies or source files changed.

A GCS (Google Cloud Storage) bucket is now mounted as a FUSE filesystem volume
at `/home/agent/.gradle/caches` in the Cloud Run job. Gradle writes its compile
cache to this directory and reads it back on subsequent runs.

## How It Works

Cloud Run supports mounting GCS buckets as FUSE volumes — the container sees
it as a normal directory. Reads and writes go to GCS transparently.

```
First run (cold):
  Container starts → /home/agent/.gradle/caches is empty (bucket empty)
  Gradle compiles everything → writes bytecode to .gradle/caches
  Writes persist to GCS bucket automatically via FUSE
  Container exits

Second run (warm):
  Container starts → .gradle/caches already has compiled classes from GCS
  Gradle sees cache → skips recompilation
  Run completes 3-4 min faster
```

## GCP Setup

### Bucket
- Name: `media-sage-gradle-cache`
- Location: `us-central1` (same region as Cloud Run job — zero egress cost)
- Access control: Uniform bucket-level access
- Lifecycle: Delete objects older than 30 days (prevents unbounded growth)

### IAM
Service account `media-sage-orchestrator@media-sage-agent.iam.gserviceaccount.com`
granted `roles/storage.objectAdmin` on the bucket.

### Cloud Run Job Volume Mount
```bash
gcloud run jobs update media-sage-agent-worker \
  --add-volume=name=gradle-cache,type=cloud-storage,bucket=media-sage-gradle-cache \
  --add-volume-mount=volume=gradle-cache,mount-path=/home/agent/.gradle/caches \
  --region=us-central1 \
  --project=media-sage-agent
```

The mount uses the `gcsfuse.run.googleapis.com` CSI driver — no code changes
to the worker image or entrypoint were required.

## Expected Impact

| Scenario | Before | After (warm cache) |
|---|---|---|
| Targeted test (Kotlin change) | ~4-5 min Gradle | ~30s |
| Full module test (fallback) | ~4-5 min | ~1-2 min |
| SQL/doc only (skip Gradle) | 0 min | 0 min |

Pairs with MS-203 (targeted test execution) — the combination brings
Kotlin-change worker runs from ~14 min down to ~9-10 min.

## Tradeoffs

- **GCS FUSE latency**: File access is slower than local disk. Gradle does
  many small reads during cache lookup. Net is still positive vs cold compile.
- **Concurrent executions**: Two workers writing to the same cache simultaneously
  can corrupt entries. Acceptable at current scale (one worker at a time).
- **Cache invalidation**: Gradle handles stale entries automatically by version.
  The 30-day lifecycle rule prevents unbounded bucket growth.

## Key Learning

GCS FUSE volumes in Cloud Run require zero code changes — it's pure
infrastructure. The mount path `/home/agent/.gradle/caches` matches Gradle's
default `GRADLE_USER_HOME/caches` structure, so no environment variables or
Gradle configuration needed.
