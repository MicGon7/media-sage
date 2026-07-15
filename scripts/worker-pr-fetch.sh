#!/usr/bin/env bash
# Fetches a PR under review — metadata, diff, and a checked-out working tree.
#
# For the pr-quality-work review path (read-only). The repo is already cloned;
# this script fetches the PR head and checks it out, falling back to a plain
# `git fetch` + `git show` path when `gh pr checkout` is unavailable — so the
# review agent never has to improvise checkout recovery in its own loop.
#
# Usage:
#   ./scripts/worker-pr-fetch.sh PR_NUMBER
#
# Output:
#   /tmp/worker_pr.json      — PR metadata (headRefName, baseRefName, title, body, files)
#   /tmp/worker_pr_diff.txt  — full PR diff
#   /tmp/worker_pr.env       — exports WORKER_PR_HEAD_REF, WORKER_PR_BASE_REF,
#                              WORKER_PR_TITLE, WORKER_PR_CHECKOUT
#                              WORKER_PR_CHECKOUT=working-tree → head checked out; read files in place
#                              WORKER_PR_CHECKOUT=fetch-only   → read files via `git show origin/<head>:<path>`
#
# Exit codes:
#   0 — metadata + diff fetched; head checked out or available as origin/<head>
#   1 — usage error or gh/git failure

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 PR_NUMBER" >&2
    exit 1
fi

PR_NUMBER="$1"

echo ""
echo "═══════════════════════════════════════════"
echo " worker-pr-fetch  —  PR #$PR_NUMBER"
echo "═══════════════════════════════════════════"
echo ""

# ── 1. Metadata + diff ─────────────────────────────────────────────────────────

echo "Fetching PR metadata and diff..."
gh pr view "$PR_NUMBER" --json headRefName,baseRefName,title,body,files > /tmp/worker_pr.json
gh pr diff "$PR_NUMBER" > /tmp/worker_pr_diff.txt

HEAD_REF=$(python3 -c "import json; print(json.load(open('/tmp/worker_pr.json'))['headRefName'])")
BASE_REF=$(python3 -c "import json; print(json.load(open('/tmp/worker_pr.json'))['baseRefName'])")
PR_TITLE=$(python3 -c "import json; print(json.load(open('/tmp/worker_pr.json'))['title'])")

# ── 2. Check out the head ──────────────────────────────────────────────────────

# Make the head ref available locally regardless of how we read it below.
git fetch origin "$HEAD_REF" --quiet

# Prefer a real checkout so the review sees the full working tree
# (siblings, module CLAUDE.md — not just the diff).
if git checkout "$HEAD_REF" --quiet 2>/dev/null \
    || git checkout -b "$HEAD_REF" "origin/$HEAD_REF" --quiet 2>/dev/null; then
    CHECKOUT_STATE=working-tree
    echo "✅  Checked out PR head: $HEAD_REF"
else
    CHECKOUT_STATE=fetch-only
    echo "⚠️  Could not check out $HEAD_REF — read files via: git show origin/$HEAD_REF:<path>"
fi

# ── 3. Write env file ──────────────────────────────────────────────────────────

printf 'export WORKER_PR_HEAD_REF=%q\nexport WORKER_PR_BASE_REF=%q\nexport WORKER_PR_TITLE=%q\nexport WORKER_PR_CHECKOUT=%q\n' \
    "$HEAD_REF" "$BASE_REF" "$PR_TITLE" "$CHECKOUT_STATE" > /tmp/worker_pr.env

echo ""
echo "✅  PR fetched — #$PR_NUMBER ($HEAD_REF → $BASE_REF)"
echo "    Metadata: /tmp/worker_pr.json"
echo "    Diff:     /tmp/worker_pr_diff.txt"
echo "    Env:      /tmp/worker_pr.env (WORKER_PR_CHECKOUT=$CHECKOUT_STATE)"
echo ""
