#!/bin/bash
# Fetch the worker run log attachment from a Jira ticket.
# Usage: ./scripts/judge-fetch-runlog.sh <TICKET_KEY>
# Output: writes /tmp/worker-run.jsonl if found; exits 0 on success, 1 if not found.
# Requires: JIRA_BOT_EMAIL, JIRA_BOT_API_TOKEN
set -eo pipefail

TICKET_KEY="$1"
if [ -z "$TICKET_KEY" ]; then
  echo "Usage: $0 <TICKET_KEY>" >&2
  exit 1
fi

content_url=$(curl -sf \
  -u "${JIRA_BOT_EMAIL}:${JIRA_BOT_API_TOKEN}" \
  "https://media-sage.atlassian.net/rest/api/3/issue/${TICKET_KEY}?fields=attachment" \
  | python3 -c "
import json, sys
data = json.load(sys.stdin)
for a in data.get('fields', {}).get('attachment', []):
    if a['filename'].startswith('worker-run-') and a['filename'].endswith('.jsonl'):
        print(a['content'])
        break
" 2>/dev/null)

if [ -z "$content_url" ]; then
  echo "No worker run log found for ${TICKET_KEY}" >&2
  exit 1
fi

curl -sf \
  -u "${JIRA_BOT_EMAIL}:${JIRA_BOT_API_TOKEN}" \
  "$content_url" \
  -o /tmp/worker-run.jsonl

echo "Run log downloaded to /tmp/worker-run.jsonl"
