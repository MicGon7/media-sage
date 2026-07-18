#!/usr/bin/env bash
# Sets up the feature branch for a worker job.
#
# Checks for an existing open PR first — if one is found the worker should
# check out that branch and continue rather than creating a new one.
#
# Usage:
#   ./scripts/worker-init.sh TICKET_KEY BRANCH_DESCRIPTION
#
#   TICKET_KEY          e.g. MS-354
#   BRANCH_DESCRIPTION  e.g. reduce-worker-turns  (no spaces, kebab-case)
#
# Exit codes:
#   0 — branch ready (new or existing); WORKER_BRANCH_STATUS written to /tmp/worker_init.env
#       WORKER_BRANCH_STATUS=new       → fresh branch, no PR yet
#       WORKER_BRANCH_STATUS=existing  → checked out existing branch, PR URL in WORKER_PR_URL
#   1 — usage error or git failure

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 TICKET_KEY [BRANCH_DESCRIPTION]" >&2
    exit 1
fi

TICKET_KEY="$1"

if [ $# -ge 2 ]; then
    BRANCH_DESC="$2"
elif [ -n "${TICKET_SUMMARY:-}" ]; then
    BRANCH_DESC=$(TICKET_SUMMARY="$TICKET_SUMMARY" python3 -c "
import re, os
s = os.environ['TICKET_SUMMARY']
slug = re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')[:50].rstrip('-')
print(slug)
")
else
    echo "Usage: $0 TICKET_KEY [BRANCH_DESCRIPTION]  (or set TICKET_SUMMARY in env)" >&2
    exit 1
fi
BRANCH_NAME="feature/${TICKET_KEY}-${BRANCH_DESC}"

echo ""
echo "═══════════════════════════════════════════"
echo " worker-init  —  $TICKET_KEY"
echo "═══════════════════════════════════════════"
echo ""

# Precompute discovery searches the model would otherwise spend turns on.
#
# Render-test coverage: which composables already have a `captureRoboImage` block.
# The discovery phase (ticket-work.md step 2) and the render step (step 5) need to
# know, for an affected screen, whether a render block already exists. Computing it
# here means the model reads the answer from this output instead of issuing its own
# grep — one fewer serial search in the discovery phase.
RENDER_COVERED=$(python3 - << 'PYEOF'
import glob
import re

# captureRoboImage(...) { MediaSageTheme(...) { <Composable>( ... — capture the composable.
pattern = re.compile(
    r"captureRoboImage\([^)]*\)\s*\{\s*MediaSageTheme\([^)]*\)\s*\{\s*([A-Za-z_]\w*)\s*\(",
    re.DOTALL,
)
covered = []
for path in glob.glob("composeApp/src/**/*.kt", recursive=True):
    if "androidUnitTest" not in path:
        continue
    with open(path, encoding="utf-8", errors="ignore") as f:
        text = f.read()
    if "captureRoboImage" not in text:
        continue
    for name in pattern.findall(text):
        if name not in covered:
            covered.append(name)
print(" ".join(covered))
PYEOF
)

if [ -n "$RENDER_COVERED" ]; then
    echo "Render-test coverage (composables with an existing captureRoboImage block):"
    echo "    $RENDER_COVERED"
else
    echo "Render-test coverage: none — no captureRoboImage blocks found."
fi
echo ""

# Check for an existing open PR targeting this branch.
EXISTING=$(gh pr list --state open --search "head:feature/${TICKET_KEY}" \
    --json number,url,headRefName --limit 1 2>/dev/null || echo "[]")

PR_COUNT=$(echo "$EXISTING" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")

if [ "$PR_COUNT" -gt 0 ]; then
    PR_URL=$(echo "$EXISTING" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['url'])")
    HEAD_REF=$(echo "$EXISTING" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['headRefName'])")
    echo "Existing PR found: $PR_URL"
    echo "Checking out branch: $HEAD_REF"
    git fetch origin "$HEAD_REF"
    git checkout -b "$HEAD_REF" origin/"$HEAD_REF"
    printf 'export WORKER_BRANCH_STATUS=existing\nexport WORKER_PR_URL=%s\nexport WORKER_BRANCH_NAME=%s\nexport WORKER_RENDER_COVERED="%s"\n' \
        "$PR_URL" "$HEAD_REF" "$RENDER_COVERED" > /tmp/worker_init.env
    echo ""
    echo "✅  Branch ready (existing) — $HEAD_REF"
    echo "    PR: $PR_URL"
    exit 0
fi

# No existing PR — create a fresh branch from main.
echo "No existing PR found. Creating branch: $BRANCH_NAME"
git fetch origin
git checkout -b "$BRANCH_NAME" origin/main
printf 'export WORKER_BRANCH_STATUS=new\nexport WORKER_PR_URL=\nexport WORKER_BRANCH_NAME=%s\nexport WORKER_RENDER_COVERED="%s"\n' \
    "$BRANCH_NAME" "$RENDER_COVERED" > /tmp/worker_init.env
echo ""
echo "✅  Branch ready (new) — $BRANCH_NAME"
