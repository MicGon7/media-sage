#!/usr/bin/env bash
# Runs Gradle tests only for modules touched by changes since main.
#
# Use this inside the Cloud Run worker container instead of the static
# `./gradlew :agent:test :server:test` command. It avoids unnecessary
# cross-module test runs and reduces memory pressure on the 4GiB container.
#
# Always uses --no-daemon to avoid Gradle daemon overhead in memory-constrained
# environments. CI (GitHub Actions) runs the full suite — this script is the
# worker's targeted pre-commit gate only.
#
# Module → test task mapping (JVM-only, container-safe):
#   agent/      → :agent:test --tests "<MatchingTestClass>"
#   server/     → :server:test --tests "<MatchingTestClass>"
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
AGENT_TEST_CLASSES=()
SERVER_TEST_CLASSES=()

# Map changed source files to their corresponding test class names.
# Convention: Foo.kt → FooTest.kt (if it exists in the test source set).
find_test_class() {
    local module="$1"   # e.g. "agent"
    local src_file="$2" # e.g. "agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt"
    local base
    base=$(basename "$src_file" .kt)
    local test_file
    test_file=$(find "${module}/src/test" -name "${base}Test.kt" 2>/dev/null | head -1)
    if [ -n "$test_file" ]; then
        # Extract fully-qualified class name from the package declaration
        local pkg
        pkg=$(grep -m1 "^package " "$test_file" 2>/dev/null | awk '{print $2}')
        if [ -n "$pkg" ]; then
            echo "${pkg}.${base}Test"
        fi
    fi
}

while IFS= read -r file; do
    case "$file" in
        agent/src/main/*)
            RUN_AGENT=true
            cls=$(find_test_class "agent" "$file")
            if [ -n "$cls" ]; then
                AGENT_TEST_CLASSES+=("$cls")
            fi
            ;;
        agent/*)
            RUN_AGENT=true
            ;;
        server/src/main/*)
            RUN_SERVER=true
            cls=$(find_test_class "server" "$file")
            if [ -n "$cls" ]; then
                SERVER_TEST_CLASSES+=("$cls")
            fi
            ;;
        server/*)
            RUN_SERVER=true
            ;;
    esac
done <<< "$CHANGED"

run_tests() {
    local task="$1"
    shift
    local classes=("$@")
    if [ ${#classes[@]} -gt 0 ]; then
        local filter_args=()
        for cls in "${classes[@]}"; do
            filter_args+=("--tests" "$cls")
        done
        echo "Running targeted tests for ${task}: ${classes[*]}"
        ./gradlew --no-daemon "$task" "${filter_args[@]}"
    else
        echo "Running full test suite for ${task} (no direct test mapping found)"
        ./gradlew --no-daemon "$task"
    fi
}

RAN_ANY=false

if [ "$RUN_AGENT" = "true" ]; then
    run_tests ":agent:test" "${AGENT_TEST_CLASSES[@]+"${AGENT_TEST_CLASSES[@]}"}"
    RAN_ANY=true
fi

if [ "$RUN_SERVER" = "true" ]; then
    run_tests ":server:test" "${SERVER_TEST_CLASSES[@]+"${SERVER_TEST_CLASSES[@]}"}"
    RAN_ANY=true
fi

if [ "$RAN_ANY" = "false" ]; then
    echo "No testable modules changed (shared/composeApp/scripts require SDK or have no tests) — skipping."
fi
