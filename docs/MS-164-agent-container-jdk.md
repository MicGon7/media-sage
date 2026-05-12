# MS-164: Fix Agent Container — Switch Runtime to JDK

## What changed

Changed the agent Docker runtime base image from `eclipse-temurin:21-jre-jammy` to `eclipse-temurin:21-jdk-jammy`. Removed stale CLAUDE.md guidance that said "Gradle works fine with a JRE."

## Why it broke

Gradle 8.14.3 requires `javac` during the configuration phase to process the version catalog (`gradle/libs.versions.toml`). The JRE image has no `javac` — it lives at `/opt/java/openjdk/bin/javac` in the JDK image but is absent entirely from the JRE image. Gradle would fail before tests even started.

The original CLAUDE.md note ("Gradle works fine with JRE because the Kotlin compiler is bundled") was accurate on an older Gradle version and became stale when the project upgraded to 8.14.3.

## The bootstrapping paradox

The autonomous agent runs inside the container it is trying to fix. It cannot rebuild the image mid-run to verify its own change — it will always be executing inside the old (broken) environment. The correct behavior is what the agent did: make the trivially-correct change, note the bootstrapping constraint in the PR, and leave verification to a human.

## How to verify Dockerfile changes locally

When a Dockerfile change adds or removes a dependency, build the image and use `docker run --rm --entrypoint which` to confirm the binary is present in the runtime image:

```bash
# 1. Build the image from the branch
docker build -f agent/Dockerfile -t media-sage-agent-test .

# 2. Probe for the dependency
docker run --rm --entrypoint which media-sage-agent-test <binary>
# e.g. javac → /opt/java/openjdk/bin/javac
#      git   → /usr/bin/git
```

`which` is just an example — swap in any binary the container needs. If it prints a path, it's there. This pattern works for any dependency check without triggering the container's ENTRYPOINT (which clones the repo and starts the Ktor server).

Code correctness is validated by CI on merge, not inside the container.

## Deployment

After merging, redeploy the `:agent` service on Railway. Railway will pull the updated image on next deploy. Future autonomous runs will be able to run `./gradlew :agent:test :server:test` successfully inside the container.
