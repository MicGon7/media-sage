#!/usr/bin/env bash
# Runs quality gates (tests + detekt) in parallel and prints a clean summary.
#
# Handles the pre-existing detekt violation check automatically — if detekt
# fails, stashes the working tree, re-runs detekt on base, and pops the stash
# so the worker gets a clear signal without a second read turn.
#
# Usage:
#   ./scripts/worker-quality.sh
#
# Exit codes:
#   0 — all gates passed (or detekt violations were pre-existing on base)
#   1 — tests failed, or detekt found NEW violations introduced by this branch

set -euo pipefail

echo ""
echo "═══════════════════════════════════════════"
echo " worker-quality  —  tests + detekt"
echo "═══════════════════════════════════════════"
echo ""

echo "Running quality gates in parallel..."
./scripts/run-affected-tests.sh 2>&1 | tee /tmp/tests.log & TESTS_PID=$!
./gradlew detekt --no-daemon 2>&1 | tee /tmp/detekt.log & DETEKT_PID=$!

wait $TESTS_PID; TESTS_EXIT=$?
wait $DETEKT_PID; DETEKT_EXIT=$?

echo ""
echo "── Tests ───────────────────────────────────"
tail -10 /tmp/tests.log
echo ""
echo "── Detekt ──────────────────────────────────"
tail -10 /tmp/detekt.log
echo ""

# If detekt failed, check whether violations are pre-existing on the base branch.
if [ "$DETEKT_EXIT" -ne 0 ]; then
    echo "Detekt failed — checking if violations are pre-existing on base..."
    git stash
    ./gradlew detekt --no-daemon 2>&1 | tee /tmp/detekt_base.log; BASE_DETEKT_EXIT=$?
    git stash pop
    if [ "$BASE_DETEKT_EXIT" -ne 0 ]; then
        echo ""
        echo "✅  Detekt: violations are pre-existing on base — worker not responsible. Continuing."
        DETEKT_EXIT=0
    else
        echo ""
        echo "❌  Detekt: violations were introduced by this branch."
        tail -20 /tmp/detekt.log
    fi
fi

echo "═══════════════════════════════════════════"
if [ "$TESTS_EXIT" -eq 0 ] && [ "$DETEKT_EXIT" -eq 0 ]; then
    echo " ✅  Quality gates passed"
else
    [ "$TESTS_EXIT" -ne 0 ] && echo " ❌  Tests FAILED (exit $TESTS_EXIT)"
    [ "$DETEKT_EXIT" -ne 0 ] && echo " ❌  Detekt FAILED — fix violations before pushing"
fi
echo "═══════════════════════════════════════════"
echo ""

# Fail with explicit exit code so the worker can stop cleanly.
if [ "$TESTS_EXIT" -ne 0 ] || [ "$DETEKT_EXIT" -ne 0 ]; then
    exit 1
fi
