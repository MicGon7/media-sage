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
    printf 'WORKER_BRANCH_STATUS=existing\nWORKER_PR_URL=%s\nWORKER_BRANCH_NAME=%s\n' \
        "$PR_URL" "$HEAD_REF" > /tmp/worker_init.env
    echo ""
    echo "✅  Branch ready (existing) — $HEAD_REF"
    echo "    PR: $PR_URL"
    exit 0
fi

# No existing PR — create a fresh branch from main.
echo "No existing PR found. Creating branch: $BRANCH_NAME"
git fetch origin
git checkout -b "$BRANCH_NAME" origin/main
printf 'WORKER_BRANCH_STATUS=new\nWORKER_PR_URL=\nWORKER_BRANCH_NAME=%s\n' \
    "$BRANCH_NAME" > /tmp/worker_init.env
echo ""
echo "✅  Branch ready (new) — $BRANCH_NAME"
