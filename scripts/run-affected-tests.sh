#!/usr/bin/env bash
# Runs Gradle tests only for modules touched by changes since main.
#
# Use this inside the Cloud Run worker container instead of the static
# `./gradlew :agent:test :server:test` command. It avoids unnecessary
# cross-module test runs and reduces memory pressure on the 4GiB container.
#
# Module → test task mapping (JVM-only, container-safe):
#   agent/      → :agent:test
#   server/     → :server:test
#   shared/     → skipped (Android unit tests, requires Android SDK)
#   composeApp/ → skipped (requires Android/iOS SDK)
#   scripts/    → skipped (no tests)
#
# Usage:
#   ./scripts/run-affected-tests.sh
#
# Exit codes:
#   0 — all tests passed (or no testable modules changed)
#   1 — one or more tests failed

set -euo pipefail

CHANGED=$(git diff --name-only origin/main...HEAD 2>/dev/null || git diff --name-only main...HEAD 2>/dev/null || echo "")

if [ -z "$CHANGED" ]; then
    echo "No changes detected against main — skipping tests."
    exit 0
fi

RUN_AGENT=false
RUN_SERVER=false

while IFS= read -r file; do
    case "$file" in
        agent/*) RUN_AGENT=true ;;
        server/*) RUN_SERVER=true ;;
    esac
done <<< "$CHANGED"

TASKS=()
if [ "$RUN_AGENT" = "true" ]; then
    TASKS+=(":agent:test")
fi
if [ "$RUN_SERVER" = "true" ]; then
    TASKS+=(":server:test")
fi

if [ ${#TASKS[@]} -eq 0 ]; then
    echo "No testable modules changed (shared/composeApp/scripts require SDK or have no tests) — skipping."
    exit 0
fi

echo "Changed modules requiring tests: ${TASKS[*]}"
./gradlew "${TASKS[@]}"
