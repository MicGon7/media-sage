# MS-194 — Run Only Affected Module Tests

## Problem

The Cloud Run worker container (4GiB RAM, shared with Claude Code) was OOMing when running `./gradlew :agent:test :server:test` regardless of which modules were actually changed. A smoke test ticket that only touched `agent/` was still triggering `:server:test`, doubling memory pressure unnecessarily.

## Solution

Added `scripts/run-affected-tests.sh` — a lightweight script that diffs changed files against `main` and only runs test tasks for modules that were actually touched.

## How it works

```bash
CHANGED=$(git diff --name-only origin/main...HEAD)
# Inspects each changed file path and sets RUN_AGENT / RUN_SERVER flags
# Builds a TASKS array and runs ./gradlew "${TASKS[@]}"
```

**Module → test task mapping:**
| Module | Test task | Notes |
|--------|-----------|-------|
| `agent/` | `:agent:test` | JVM only, container-safe |
| `server/` | `:server:test` | JVM only, container-safe |
| `shared/` | skipped | Android unit tests, requires Android SDK |
| `composeApp/` | skipped | Requires Android/iOS SDK |
| `scripts/` | skipped | No tests |

## Key Learnings

- **Test only what changed.** Running the full test suite on every module is expensive in a memory-constrained container. Scoped tests are faster, cheaper, and avoid OOM failures for single-module changes.
- **`git diff --name-only origin/main...HEAD`** is the idiomatic way to find changed files on a feature branch relative to main. The `...` (three-dot) syntax compares the branch tip against the merge base, not the current state of main.
- **Exit 0 on no testable modules** is intentional — changes to `scripts/`, `CLAUDE.md`, or `docs/` are valid PRs that don't require a test run.
- **CI is still the authoritative gate.** This script reduces container OOM risk but does not replace GitHub Actions CI, which runs the full test suite including Android and iOS targets.
