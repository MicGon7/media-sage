# MS-177: CLAUDE.md OOM Stop Rule

## What Changed

Added an explicit OOM stop rule to the "Before submitting work" section of `CLAUDE.md`.

## Why

During MS-176, the autonomous agent hit an out-of-memory error when trying to run `./gradlew detekt`. Instead of stopping, it looped: checking daemon logs, running `./gradlew help`, reading `gradle.properties`, then attempting to retry with reduced JVM flags (`-Xmx512M`). It never identified that the environment itself was the problem.

The root cause was a transient Railway memory constraint (the cgroup limit was reporting ~953MB instead of the normal ~2.5GB, caused by a Railway UI/platform glitch). This is not something the agent can fix — no amount of JVM flag tuning resolves a platform-level memory restriction.

## The Pattern to Avoid

When Gradle OOMs, the agent's instinct is to diagnose and retry. That loop:
1. Wastes token budget
2. Burns Railway compute credits (each Gradle invocation costs money even if it OOMs)
3. Never resolves — the environment constraint is external

## The Rule

If any Gradle command exits with an OOM error, daemon startup failure, or cgroup memory limit error:
- Stop immediately
- Do not investigate daemon logs or run diagnostic commands
- Do not retry with alternative JVM flags
- Post a comment on the PR or Jira ticket stating the blocker
- Exit — CI is the authoritative quality gate

## When This Can Happen

- Railway trial expiry or throttling
- Transient platform memory allocation glitch (as in MS-176)
- Railway service restart mid-run
- Memory spike from a concurrent heavy run

All of these are environment problems, not code problems. The agent cannot resolve them.
