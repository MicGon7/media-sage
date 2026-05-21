# MS-209 — Replace GCS FUSE Gradle Cache with Dependency Pre-Baking in Worker Image

## Problem

After MS-204 added a GCS FUSE volume mount at `.gradle/caches`, and MS-206 narrowed that
mount to `.gradle/caches/modules-2`, the worker's `./gradlew detekt` step began failing
with plugin resolution errors:

```
Plugin [id: 'com.android.application', version: '8.11.2', apply: false] was not found
could not resolve plugin artifact 'com.android.application:com.android.application.gradle.plugin:8.11.2'
```

The EPERM error from MS-206 (gradle-api jar) was resolved, but a new failure appeared on
every cold start (empty GCS bucket).

## Root Cause: GCS FUSE is Incompatible with Gradle's Module Cache Write Path

Gradle's `modules-2` dependency cache uses POSIX semantics that GCS FUSE does not fully support:

- **File locking** — Gradle uses `flock`/`fcntl` locks when downloading and verifying
  artifacts. GCS FUSE does not support POSIX file locking.
- **Eventual consistency** — Gradle writes an artifact then immediately reads it back for
  checksum verification. GCS FUSE is eventually consistent; the file may not be visible
  immediately after write.
- **Atomic rename** — Gradle downloads to a temp path then renames atomically. Rename
  semantics differ on network-backed FUSE.

**Result:** On cold starts (empty bucket), Gradle downloads a plugin JAR to GCS FUSE,
the checksum read-back fails or the file lock is denied, and Gradle marks the artifact as
unresolvable — even though the bytes made it to GCS.

**Warm cache worked** because the bucket was pre-populated from a previous run; reads on
GCS FUSE are reliable, so Gradle found the JARs already present and skipped the write path.
The moment the bucket was cleared, the problem surfaced.

## Why GCS FUSE Was the Wrong Tool

GCS FUSE is designed for large blobs (ML model weights, media files) read sequentially
from a well-known path. It is not a POSIX-compliant filesystem and is not suitable for
tooling that relies on file locking, strong read-after-write consistency, or atomic
cross-directory renames — all of which Gradle's artifact cache relies on.

## Fix

### Dockerfile.worker — Dependency Pre-Baking

Instead of caching dependencies at runtime on a network filesystem, resolve them at
**image build time** where the local container filesystem is used:

```dockerfile
# Before (MS-206 state)
COPY --chown=agent:agent gradlew gradlew
COPY --chown=agent:agent gradle/wrapper gradle/wrapper
RUN chmod +x gradlew && ./gradlew help --no-daemon && rm -f gradlew && rm -rf gradle

# After (MS-209)
COPY --chown=agent:agent gradlew gradlew
COPY --chown=agent:agent gradle/ gradle/
COPY --chown=agent:agent settings.gradle.kts settings.gradle.kts
COPY --chown=agent:agent build.gradle.kts build.gradle.kts
COPY --chown=agent:agent agent/build.gradle.kts agent/build.gradle.kts
COPY --chown=agent:agent server/build.gradle.kts server/build.gradle.kts
RUN chmod +x gradlew && \
    ./gradlew help --no-daemon && \
    ./gradlew :agent:dependencies :server:dependencies --no-daemon --configure-on-demand && \
    rm -f gradlew && rm -rf gradle
```

Key points:
- `./gradlew help --no-daemon` — generates `gradle-api-8.14.3.jar` (MS-206 fix, kept)
- `./gradlew :agent:dependencies :server:dependencies --no-daemon --configure-on-demand` —
  downloads all `:agent` and `:server` runtime/test JARs into the image layer
- `--configure-on-demand` — Gradle only evaluates the build files of requested modules;
  `:composeApp` and `:shared` are never configured, so no Android SDK is required in this
  image (and no Android SDK is installed here)
- The build files (settings + root + agent + server `build.gradle.kts`) are copied in but
  source code is not — source files are not needed to resolve dependencies

### Cloud Run Job — Remove GCS Volume Mount

The `gradle-cache` volume and its mounts were removed from the Cloud Run job
`media-sage-agent-worker`. The GCS bucket `media-sage-gradle-cache` is no longer used.

```bash
# Volume mount removed via REST API PATCH (gcloud jobs update had a bug with dual mounts)
# Net result: no volumeMounts, no volumes on the job spec
```

## What Was Tried First (MS-204 + MS-206 Attempts)

| Attempt | Change | Result |
|---|---|---|
| MS-204 | Mount GCS at `.gradle/caches` | Warm-cache reads worked; cold-start writes silently failed |
| MS-206 attempt 1 | Narrow mount to `.gradle/caches/modules-2` | Added new mount without removing old; EPERM persisted |
| MS-206 fix | `./gradlew help` in Dockerfile pre-bakes `gradle-api-8.14.3.jar` | EPERM resolved; but dual mount hid the pre-baked jar |
| MS-209 | Remove GCS mount; pre-bake deps in image | Correct fix |

## Key Learnings

1. **GCS FUSE ≠ local disk.** It works for sequential large-blob reads, not for POSIX
   tooling like Gradle that needs file locking and strong consistency on writes.

2. **Warm-cache illusions.** A caching scheme that works when warm but fails cold is
   worse than no caching — it passes smoke tests and fails in production resets.

3. **`--configure-on-demand` is the right flag for JVM-only subproject tasks.** It
   prevents Gradle from evaluating Android/iOS build files in a JVM-only Docker image.

4. **Pre-bake at build time, not runtime.** Dependencies should be resolved when the
   image is built (stable network, local filesystem, predictable). Runtime dependency
   downloads are a liability in containerized environments with restricted filesystem
   semantics.

5. **Detekt needs detekt plugin downloaded.** Even running `./gradlew detekt` on a fresh
   container requires Gradle to download the detekt plugin at configuration time. Pre-baking
   via `:agent:dependencies` + `:server:dependencies` pulls those plugin JARs transitively.
