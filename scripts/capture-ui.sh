#!/usr/bin/env bash
# Renders the composeApp UI capture tests to PNGs and prepares them for the PR.
#
# This is the MECHANICAL half of the MS-581 render loop — it does the parts that
# need no judgement so they cost the worker zero reasoning turns:
#   1. Run the headless Roborazzi render (Android target only, no emulator).
#   2. Copy every produced PNG into a tracked directory on the branch.
#   3. Stage them so the ship flow commits them.
#   4. Print a ready-to-paste Markdown block embedding each image by raw URL.
#
# The JUDGEMENT half stays with the agent: deciding which screens changed, writing
# a captureRoboImage { } block per screen with representative state, and — the whole
# point — opening the PNGs to critique spacing/layout before shipping.
#
# The iOS targets are skipped via -Pmediasage.worker=true so the Kotlin/Native
# toolchain (~3 GB) is never downloaded on the Linux worker. Always --no-daemon to
# avoid Gradle daemon memory pressure in the 4 GiB Cloud Run container.
#
# Usage:
#   ./scripts/capture-ui.sh
#
# Exit codes:
#   0 — render succeeded, PNGs staged and Markdown printed
#   1 — no capture tests produced any PNG (nothing to attach)
#   2 — the render task itself failed

set -euo pipefail

RENDER_OUT="composeApp/build/outputs/roborazzi"
COMMIT_DIR="docs/ui-screenshots"

# The render compiles the composeApp Android target, which needs a COMPILE-TIME
# Android SDK (android.jar + aapt2). Robolectric only swaps android.jar at runtime,
# so it does not remove the compile-time dependency. The Cloud Run worker gains this
# SDK in a follow-up image update; until then, skip loudly and non-fatally (exit 3)
# so a UI ticket does not break the whole run. Exit 3 means "render skipped", not
# "render failed" — callers treat it as skip, never as a quality-gate failure.
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && \
   ! grep -q '^sdk.dir=' local.properties 2>/dev/null; then
    echo "NOTICE: No Android SDK found (ANDROID_HOME / ANDROID_SDK_ROOT / sdk.dir)." >&2
    echo "NOTICE: Skipping UI render — worker image render support is pending." >&2
    exit 3
fi

echo "Rendering composeApp UI captures (headless, Android target only)..."
if ! ./gradlew :composeApp:recordRoborazziDebug -Pmediasage.worker=true --no-daemon; then
    echo "ERROR: render task failed." >&2
    exit 2
fi

shopt -s nullglob
pngs=("$RENDER_OUT"/*.png)
if [ ${#pngs[@]} -eq 0 ]; then
    echo "ERROR: render produced no PNGs — is there a captureRoboImage test?" >&2
    exit 1
fi

mkdir -p "$COMMIT_DIR"
for png in "${pngs[@]}"; do
    cp "$png" "$COMMIT_DIR/"
done
git add "$COMMIT_DIR"/*.png

# Derive the raw.githubusercontent base for the current branch. The branch is
# pushed at ship time, so these URLs resolve once the PR exists.
REMOTE_URL=$(git remote get-url origin)
SLUG=$(echo "$REMOTE_URL" | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')
BRANCH=$(git rev-parse --abbrev-ref HEAD)
RAW_BASE="https://raw.githubusercontent.com/${SLUG}/${BRANCH}"

echo ""
echo "==================== PR screenshot block (copy into PR body) ===================="
echo "## UI screenshots"
echo ""
for png in "${pngs[@]}"; do
    name=$(basename "$png" .png)
    echo "**${name}**"
    echo ""
    echo "![${name}](${RAW_BASE}/${COMMIT_DIR}/$(basename "$png"))"
    echo ""
done
echo "================================================================================"
