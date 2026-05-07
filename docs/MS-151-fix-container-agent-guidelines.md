# MS-151: Fix Container Agent Guidelines

## What Was Fixed

Two gaps in the autonomous agent guidelines that caused a token-burning loop during the first real coding ticket (MS-150).

## Root Cause

### Wrong test command

`CLAUDE.md` told the agent to run `:shared:jvmTest` in the container. That task does not exist. The `:shared` module is an Android + iOS KMP module — its only test targets are Android unit tests (`testDebugUnitTest`, `testReleaseUnitTest`) which require the Android SDK, and `iosSimulatorArm64Test` which requires the iOS SDK. Neither is available in the Linux container.

The agent hit a Gradle "task not found" error, then looped trying workarounds (checking for JDK, inspecting the task graph, attempting alternative invocations) until the token budget was exhausted.

Verified locally: `./gradlew :agent:test :server:test` passes. `:shared:jvmTest` has never existed.

### No early-exit rule

There was no instruction telling the agent to stop when it encountered an unresolvable environment blocker. The agent's default behavior is to keep trying — which is usually good, but catastrophic when the blocker is a missing SDK that can't be self-installed.

## Changes

### Corrected container test command

```
# Before (wrong)
./gradlew :agent:test :server:test :shared:jvmTest

# After (correct)
./gradlew :agent:test :server:test
```

### Added early-exit rule to Agent Guidelines

If a required tool, SDK, or Gradle task is missing and cannot be self-resolved without elevated access or SDK installation, the agent must stop immediately, post a comment on the PR or Jira ticket describing the blocker, and exit. This prevents token waste from looping on unresolvable conditions.

## Files Changed

- `CLAUDE.md` — corrected container test command, added early-exit rule
