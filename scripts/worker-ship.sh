#!/usr/bin/env bash
# Commits, pushes, opens a PR, updates Jira AC checkboxes, and transitions the
# Jira ticket to In Review — all in a single tool call.
#
# Expects:
#   /tmp/pr_body.md    — PR description (written by worker before calling this script)
#
# Usage:
#   ./scripts/worker-ship.sh TICKET_KEY "MS-XXX: Commit message"
#
# Required env vars:
#   JIRA_BOT_EMAIL        — bot Jira email
#   JIRA_BOT_API_TOKEN    — bot Jira API token
#
# Exit codes:
#   0 — PR opened, Jira updated, ticket transitioned to In Review; PR URL printed
#   1 — any step failed

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Usage: $0 TICKET_KEY \"COMMIT_MSG\"" >&2
    exit 1
fi

TICKET_KEY="$1"
COMMIT_MSG="$2"
JIRA_BASE="https://media-sage.atlassian.net/rest/api/3/issue/${TICKET_KEY}"

: "${JIRA_BOT_EMAIL:?Set JIRA_BOT_EMAIL}"
: "${JIRA_BOT_API_TOKEN:?Set JIRA_BOT_API_TOKEN}"

if [ ! -f /tmp/pr_body.md ]; then
    echo "❌  /tmp/pr_body.md not found — write the PR body before calling worker-ship.sh" >&2
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════"
echo " worker-ship  —  $TICKET_KEY"
echo "═══════════════════════════════════════════"
echo ""

# ── 1. Commit + push ──────────────────────────────────────────────────────────

echo "Staging and committing..."
git add -A
git commit -m "$COMMIT_MSG"

echo "Pushing..."
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
git push --force-with-lease -u origin "$CURRENT_BRANCH"

# ── 2. Open PR ────────────────────────────────────────────────────────────────

echo "Opening PR..."
PR_URL=$(gh pr create --title "$COMMIT_MSG" --body-file /tmp/pr_body.md)
echo "PR: $PR_URL"

# ── 3. Update Jira AC checkboxes ─────────────────────────────────────────────

echo "Updating Jira AC checkboxes..."
DESC=$(curl -s -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" "$JIRA_BASE" \
    | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin)['fields']['description']))")

UPDATED=$(echo "$DESC" | python3 -c "
import sys, json
adf = json.load(sys.stdin)
text = json.dumps(adf)
text = text.replace('[  ]', '[x]').replace('[ ]', '[x]')
print(text)
")

curl -s -X PUT -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"fields\":{\"description\":$UPDATED}}" \
    "$JIRA_BASE" > /dev/null
echo "✅  AC checkboxes updated"

# ── 4. Transition to In Review ────────────────────────────────────────────────

echo "Transitioning $TICKET_KEY to In Review..."
IN_REVIEW_ID=$(curl -s -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
    "${JIRA_BASE}/transitions" \
    | python3 -c "
import sys, json
transitions = json.load(sys.stdin)['transitions']
match = next((t['id'] for t in transitions if t['name'].lower() == 'in review'), None)
if not match:
    raise SystemExit('No \"In Review\" transition found for $TICKET_KEY')
print(match)
")
curl -s -X POST -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"transition\":{\"id\":\"$IN_REVIEW_ID\"}}" \
    "${JIRA_BASE}/transitions" > /dev/null
echo "✅  Ticket transitioned to In Review"

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════"
echo " ✅  Shipped"
echo "    PR:     $PR_URL"
echo "    Branch: $CURRENT_BRANCH"
echo "    Jira:   $TICKET_KEY → In Review"
echo "═══════════════════════════════════════════"
echo ""

# Write PR URL to a temp file so the caller can embed it in /tmp/jira_comment.txt.
echo "$PR_URL" > /tmp/worker_pr_url.txt
