#!/usr/bin/env bash
# Runs targeted Gradle tests for Kotlin files changed since main.
#
# Detects committed, staged, and unstaged changes so it works correctly
# before or after committing. Skips Gradle entirely for non-Kotlin changes
# (SQL, docs, config) — CI handles the full suite.
#
# Always uses --no-daemon to avoid Gradle daemon memory pressure in the
# 4GiB Cloud Run container.
#
# Decision table:
#   No .kt files changed            → skip entirely, delegate to CI
#   .kt changed, test class found   → ./gradlew :module:test --tests "X" --no-daemon
#   .kt changed, no test mapping    → skip, delegate to CI
#   shared/composeApp               → always skipped (requires SDK or has no tests)
#   scripts                         → :scripts:compileKotlin (no tests, but compile verifies imports)
#
# Usage:
#   ./scripts/run-affected-tests.sh
#
# Exit codes:
#   0 — all tests passed (or skipped — CI is the authoritative gate)
#   1 — one or more tests failed

set -euo pipefail

# Collect all changed files: committed, staged, and unstaged.
# This ensures the script works before committing (workers run tests pre-commit).
COMMITTED=$(git diff --name-only origin/main...HEAD 2>/dev/null || git diff --name-only main...HEAD 2>/dev/null || echo "")
STAGED=$(git diff --name-only --cached 2>/dev/null || echo "")
UNSTAGED=$(git diff --name-only HEAD 2>/dev/null || echo "")
CHANGED=$(printf '%s\n%s\n%s' "$COMMITTED" "$STAGED" "$UNSTAGED" | sort -u | grep -v '^$' || echo "")

if [ -z "$CHANGED" ]; then
    echo "No changes detected — skipping tests, delegating to CI."
    exit 0
fi

# Kotlin gate: only run Gradle if .kt source files changed.
# SQL, docs, config, and script changes don't need a Gradle test run.
KOTLIN_CHANGED=$(echo "$CHANGED" | grep '\.kt$' | grep '/src/main/' || echo "")
if [ -z "$KOTLIN_CHANGED" ]; then
    echo "No Kotlin source files changed — skipping tests, delegating to CI."
    exit 0
fi

RUN_ORCHESTRATOR=false
RUN_APPSERVER=false
RUN_SCRIPTS_COMPILE=false
ORCHESTRATOR_TEST_CLASSES=()
APPSERVER_TEST_CLASSES=()

# Map a changed source file to its corresponding test class name.
# Convention: Foo.kt → FooTest.kt (standard Kotlin/Java naming).
find_test_class() {
    local module="$1"   # e.g. "orchestrator"
    local src_file="$2" # e.g. "orchestrator/src/main/kotlin/com/mediasage/orchestrator/service/AgentLaunchService.kt"
    local base
    base=$(basename "$src_file" .kt)
    local test_file
    test_file=$(find "${module}/src/test" -name "${base}Test.kt" 2>/dev/null | head -1)
    if [ -n "$test_file" ]; then
        local pkg
        pkg=$(grep -m1 "^package " "$test_file" 2>/dev/null | awk '{print $2}')
        if [ -n "$pkg" ]; then
            echo "${pkg}.${base}Test"
        fi
    fi
}

while IFS= read -r file; do
    case "$file" in
        orchestrator/src/main/*.kt)
            RUN_ORCHESTRATOR=true
            cls=$(find_test_class "orchestrator" "$file")
            if [ -n "$cls" ]; then
                ORCHESTRATOR_TEST_CLASSES+=("$cls")
            fi
            ;;
        appServer/src/main/*.kt)
            RUN_APPSERVER=true
            cls=$(find_test_class "appServer" "$file")
            if [ -n "$cls" ]; then
                APPSERVER_TEST_CLASSES+=("$cls")
            fi
            ;;
        scripts/src/main/*.kt)
            RUN_SCRIPTS_COMPILE=true
            ;;
    esac
done <<< "$KOTLIN_CHANGED"

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
        echo "No test class mapping found for ${task} — delegating to CI."
    fi
}

RAN_ANY=false

if [ "$RUN_ORCHESTRATOR" = "true" ]; then
    run_tests ":orchestrator:test" "${ORCHESTRATOR_TEST_CLASSES[@]+"${ORCHESTRATOR_TEST_CLASSES[@]}"}"
    RAN_ANY=true
fi

if [ "$RUN_APPSERVER" = "true" ]; then
    run_tests ":appServer:test" "${APPSERVER_TEST_CLASSES[@]+"${APPSERVER_TEST_CLASSES[@]}"}"
    RAN_ANY=true
fi

if [ "$RUN_SCRIPTS_COMPILE" = "true" ]; then
    echo "Compiling :scripts to verify imports resolve..."
    ./gradlew :scripts:compileKotlin --no-daemon
    RAN_ANY=true
fi

if [ "$RAN_ANY" = "false" ]; then
    echo "No testable Kotlin changes found — delegating to CI."
fi
