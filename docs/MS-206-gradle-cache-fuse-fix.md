# MS-206 — Fix GCS FUSE Operation Not Permitted Error in Cloud Run Worker

## Problem

Every worker run failed when running `./gradlew detekt` with:

```
Failed to create Jar file /home/agent/.gradle/caches/8.14.3/generated-gradle-jars/gradle-api-8.14.3.jar.
> /home/agent/.gradle/caches/8.14.3/generated-gradle-jars/gradle-api-8.14.3.jar: Operation not permitted
```

The GCS FUSE mount covers `.gradle/caches/modules-2`. The `8.14.3/generated-gradle-jars/` path is a sibling on the local container filesystem — but Gradle still failed to create the jar there.

## Root Cause

`./gradlew --version` (used during image build to pre-bake the Gradle wrapper) is too minimal. It downloads and extracts the Gradle distribution but does not trigger the generation of `gradle-api-8.14.3.jar`. Gradle generates this jar lazily the first time real tasks run.

At runtime in Cloud Run, certain file creation syscalls used by Gradle to generate this jar are blocked by the container's security profile. The operation fails with EPERM regardless of whether the target path is on GCS FUSE or the local container filesystem.

**Verified locally:** Running `./gradlew help --no-daemon` generates the jar:

```
~/.gradle/caches/8.14.3/generated-gradle-jars/
├── generated-gradle-jars.lock
└── gradle-api-8.14.3.jar   ← this is the missing file
```

## Fix

### Dockerfile.worker

Replace `--version` with `help` in the image build step:

```dockerfile
# Before
RUN chmod +x gradlew && ./gradlew --version --no-daemon && rm -f gradlew && rm -rf gradle

# After
RUN chmod +x gradlew && ./gradlew help --no-daemon && rm -f gradlew && rm -rf gradle
```

`./gradlew help` is still a lightweight command (no source files needed, no compilation) but it triggers enough of the Gradle task graph to generate `gradle-api-8.14.3.jar`. The jar is now baked into the image — Gradle finds it already present at runtime and skips creation entirely.

### GCS Bucket Reset

The bucket was previously populated when the mount was at `.gradle/caches` (bucket root mapped to the whole caches directory). After MS-206's mount change to `.gradle/caches/modules-2`, the bucket content had the wrong structure. The bucket was cleared so it repopulates cleanly with the correct `modules-2` dependency cache on the next run.

## What Was Tried First (MS-206 Attempt 1)

The initial fix narrowed the Cloud Run volume mount from `.gradle/caches` to `.gradle/caches/modules-2`, expecting the `8.14.3/` sibling to be on local filesystem and writable. The error persisted identically — confirming the issue is a syscall restriction in the container, not a FUSE path problem.

## Key Learning

`./gradlew --version` is insufficient for pre-warming the Gradle cache in a Docker image. Use `./gradlew help --no-daemon` — it's still fast (no compilation, no source needed) but generates the internal Gradle API jars that tasks like `detekt` depend on.

The broader lesson: Cloud Run containers run with a restricted seccomp profile. File operations that work on a normal Linux host (certain jar creation patterns) may fail with EPERM in Cloud Run. The correct mitigation is to pre-generate those files during image build rather than trying to adjust mount paths or permissions at runtime.
