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
# Scoping (MS-693): recordRoborazziDebug renders every captureRoboImage block in the
# whole app, not just the screen a ticket touched — with 15+ blocks now accumulated
# across the codebase, an unscoped render became the dominant cost in the render step
# and contributed to at least one job hitting its 30-minute timeout. Pass one or more
# fully-qualified render-test class names to scope the render to only those classes via
# Gradle's --tests filter. Omitting all class names renders everything (the old,
# unscoped behaviour) — only intended for a full local sanity check, never for a
# per-ticket worker run.
#
# Usage:
#   ./scripts/capture-ui.sh [TestClass1] [TestClass2] ...
#   ./scripts/capture-ui.sh com.mediasage.feature.you.ReaderScreenRenderTest
#
# Exit codes:
#   0 — render succeeded, PNGs staged and Markdown printed
#   1 — no capture tests produced any PNG (nothing to attach)
#   2 — the render task itself failed
#   3 — render skipped (no SDK, or WORKER_SKIP_UI_RENDER set)

set -euo pipefail

RENDER_OUT="composeApp/build/outputs/roborazzi"
COMMIT_DIR="docs/ui-screenshots"

# Kill switch: the render loop is temporarily disabled worker-side. The ~12-minute
# Roborazzi build plus a no-tooling (no Pillow/ImageMagick) manual PNG-inspection
# loop was consuming most of the 30-minute Cloud Run job budget and, on at least one
# run, killing the job before a PR was opened. Set WORKER_SKIP_UI_RENDER=true on the
# worker Cloud Run Job to skip loudly and non-fatally until image-inspection tooling
# is added to the worker image and the render loop is proven to converge.
if [ "${WORKER_SKIP_UI_RENDER:-}" = "true" ]; then
    echo "NOTICE: WORKER_SKIP_UI_RENDER=true — skipping UI render (temporarily disabled)." >&2
    exit 3
fi

# The render compiles the composeApp Android target, which needs a COMPILE-TIME
# Android SDK (android.jar + aapt2). Robolectric only swaps android.jar at runtime,
# so it does not remove the compile-time dependency. The Cloud Run worker image ships
# this SDK (MS-583, installed in Dockerfile.worker), so the render normally runs. This
# guard remains as a defensive fallback for SDK-less environments (e.g. a bare local
# checkout): skip loudly and non-fatally (exit 3) so a UI ticket does not break the
# whole run. Exit 3 means "render skipped", not "render failed" — callers treat it as
# skip, never as a quality-gate failure.
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && \
   ! grep -q '^sdk.dir=' local.properties 2>/dev/null; then
    echo "NOTICE: No Android SDK found (ANDROID_HOME / ANDROID_SDK_ROOT / sdk.dir)." >&2
    echo "NOTICE: Skipping UI render — worker image render support is pending." >&2
    exit 3
fi

# Clear any PNGs left over from a prior render before running. In the worker this is
# a no-op (fresh clone, empty build dir every run), but scoping via --tests only
# renders the classes passed in — without this, a stale PNG from an earlier, broader
# render would still be picked up by the glob below and misreported as part of this run.
rm -f "$RENDER_OUT"/*.png

# --rerun-tasks (MS-693): the project enables org.gradle.caching=true, and this task's
# up-to-date/build-cache state does not appear to account for the --tests filter —
# both UP-TO-DATE and FROM-CACHE were observed locally restoring a previous, broader
# render's full output set (every screen) even when scoped to a single test class,
# which would silently misreport unrelated/stale screens as part of this run.
# --no-build-cache alone did not fix this (it only disables the build-cache backend,
# not the separate up-to-date check); --rerun-tasks forces genuine execution every
# time. This does not sacrifice the intended speed-up: MS-583/589's warm-render
# numbers came from the pre-baked ~/.gradle and ~/.m2 DEPENDENCY caches, which
# --rerun-tasks does not touch — only this task's own up-to-date/output-cache state.
GRADLE_ARGS=(":composeApp:recordRoborazziDebug" "-Pmediasage.worker=true" "--no-daemon" "--rerun-tasks")
for cls in "$@"; do
    GRADLE_ARGS+=("--tests" "$cls")
done

if [ $# -gt 0 ]; then
    echo "Rendering composeApp UI captures (headless, Android target only), scoped to: $*"
else
    echo "Rendering composeApp UI captures (headless, Android target only), unscoped — renders every screen with a render test."
fi
if ! ./gradlew "${GRADLE_ARGS[@]}"; then
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

# Derive the "owner/repo" slug for the raw.githubusercontent base. Prefer the
# GITHUB_OWNER / GITHUB_REPO env vars the worker already exports; fall back to
# parsing the origin remote. The remote MUST be credential-stripped: in the
# worker, origin is https://x-access-token:TOKEN@github.com/OWNER/REPO.git, so a
# naive parse would both break the URL and LEAK THE TOKEN into the PR body. The
# sed strips scheme, any user[:pass]@ credential, the github.com host, and .git.
if [ -n "${GITHUB_OWNER:-}" ] && [ -n "${GITHUB_REPO:-}" ]; then
    SLUG="${GITHUB_OWNER}/${GITHUB_REPO}"
else
    REMOTE_URL=$(git remote get-url origin)
    SLUG=$(echo "$REMOTE_URL" | \
        sed -E 's#^[a-z]+://##; s#^[^/@]*@##; s#github\.com[:/]##; s#\.git$##')
fi
BRANCH=$(git rev-parse --abbrev-ref HEAD)
# The branch is pushed at ship time, so these URLs resolve once the PR exists.
# The repo is public, so GitHub proxies the raw URL and renders it inline.
RAW_BASE="https://raw.githubusercontent.com/${SLUG}/${BRANCH}"

echo ""
echo "==================== PR screenshot block (copy into PR body) ===================="
echo "## UI screenshots"
echo ""
for png in "${pngs[@]}"; do
    name=$(basename "$png" .png)
    echo "**${name}**"
    echo ""
    # HTML <img> (not Markdown) so the width is capped — a full-res phone render
    # is too wide inline. An absolute URL is what makes it render, not the tag.
    echo "<img src=\"${RAW_BASE}/${COMMIT_DIR}/$(basename "$png")\" alt=\"${name}\" width=\"320\" />"
    echo ""
done
echo "================================================================================"
