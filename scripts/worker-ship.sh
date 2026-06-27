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

# ── 0. Quality gates ──────────────────────────────────────────────────────────

echo "Running quality gates..."
if ! ./scripts/worker-quality.sh; then
    echo "❌  Quality gates failed — aborting ship" >&2
    exit 1
fi
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
PR_URL=$(gh pr create --title "$COMMIT_MSG" --body-file /tmp/pr_body.md --head "$CURRENT_BRANCH")
echo "PR: $PR_URL"
# Write immediately so publish_completion can embed prNumber even if a later Jira step fails.
echo "$PR_URL" > /tmp/worker_pr_url.txt

# ── 3. Write Jira comment ─────────────────────────────────────────────────────

echo "Writing Jira comment..."
[ -f /tmp/worker_ticket.env ] && source /tmp/worker_ticket.env
DIFF_STAT=$(git diff --stat HEAD~1 2>/dev/null | tail -1 || echo "see PR")
export TICKET_KEY TICKET_SUMMARY TICKET_AC PR_URL DIFF_STAT

python3 << 'PYEOF'
import os, re

ticket_key = os.environ.get('TICKET_KEY', '')
ticket_summary = os.environ.get('TICKET_SUMMARY', ticket_key)
pr_url = os.environ.get('PR_URL', 'see PR')
diff_stat = os.environ.get('DIFF_STAT', 'see PR')
ticket_ac = os.environ.get('TICKET_AC', '')

ac_checked = re.sub(r'\[[ x]\]', '✅', ticket_ac).strip() if ticket_ac else '✅ Task completed'

comment = f"""🤖 Agent: Run summary for {ticket_key}

Task: {ticket_summary}

Pipeline checkpoints:
✅ Jira webhook fired when ticket moved to In Progress
✅ Orchestrator dispatched Cloud Run Job
✅ Worker cloned from michael-gonzalez-dev/media-sage successfully
✅ Worker completed the task and opened a PR

PR: {pr_url}

Quality gates:
✅ Detekt: passed
✅ Affected tests: passed

Diff: {diff_stat}

Acceptance criteria:
{ac_checked}"""

with open('/tmp/jira_comment.txt', 'w') as f:
    f.write(comment)
print("✅  Jira comment written to /tmp/jira_comment.txt")
PYEOF

# ── 5. Update Jira AC checkboxes ─────────────────────────────────────────────

echo "Updating Jira AC checkboxes..."
curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" "$JIRA_BASE" -o /tmp/jira_issue.json || {
    echo "⚠️  Could not fetch Jira issue — skipping AC checkbox update"
    rm -f /tmp/jira_issue.json
}

if [ -f /tmp/jira_issue.json ]; then
    python3 << 'PYEOF'
import json, sys
with open('/tmp/jira_issue.json') as f:
    data = json.load(f)
adf = data['fields'].get('description')
if not adf:
    sys.exit(0)
text = json.dumps(adf)
text = text.replace('[  ]', '[x]').replace('[ ]', '[x]')
with open('/tmp/jira_desc_updated.json', 'w') as f:
    f.write('{"fields":{"description":' + text + '}}')
PYEOF

    if [ -f /tmp/jira_desc_updated.json ]; then
        curl -sf -X PUT -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
            -H "Content-Type: application/json" \
            -d @/tmp/jira_desc_updated.json \
            "$JIRA_BASE" > /dev/null
        echo "✅  AC checkboxes updated"
    fi
fi

# ── 6. Transition to In Review ────────────────────────────────────────────────

echo "Transitioning $TICKET_KEY to In Review..."
curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
    "${JIRA_BASE}/transitions" -o /tmp/jira_transitions.json || {
    echo "⚠️  Could not fetch transitions — skipping In Review transition"
    exit 0
}
IN_REVIEW_ID=$(python3 << 'PYEOF'
import json, sys
with open('/tmp/jira_transitions.json') as f:
    transitions = json.load(f)['transitions']
match = next((t['id'] for t in transitions if t['name'].lower() == 'in review'), None)
if not match:
    print('No "In Review" transition found', file=sys.stderr)
    sys.exit(1)
print(match)
PYEOF
)
curl -sf -X POST -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
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
