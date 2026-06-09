#!/bin/bash
set -e

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-worker}"
# GitHub App noreply email — deterministic from the App ID, no env var needed
git config --global user.email "${GITHUB_APP_ID}+media-sage-worker[bot]@users.noreply.github.com"

# Publish a Pub/Sub completion event on any exit — success, failure, or early error
# (e.g. token generation failure, set -e killing the script mid-run).
# Uses the GCP metadata server for auth — always available in Cloud Run, independent
# of whether GITHUB_TOKEN was successfully generated.
# A publish failure is logged as a warning and does not affect the exit code; the
# orchestrator's recoverInterruptedJobs() handles the rare case where the event is lost.
publish_completion() {
  local exit_code=$1
  if [ -z "$PUBSUB_TOPIC" ] || [ -z "$GCP_PROJECT_ID" ]; then
    echo "PUBSUB_TOPIC or GCP_PROJECT_ID not set — skipping Pub/Sub notification"
    return
  fi

  local status
  status=$([ "$exit_code" -eq 0 ] && echo "success" || echo "failure")

  local gcp_token
  gcp_token=$(curl -sf \
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" \
    -H "Metadata-Flavor: Google" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])") || {
    echo "Warning: Failed to fetch GCP metadata token — cannot publish without auth; orchestrator will recover on restart"
    return
  }

  local message
  message=$(python3 -c "
import json, base64, os

payload = {
  'ticketKey': '${TICKET_KEY}',
  'executionName': '${CLOUD_RUN_EXECUTION}',
  'status': '$status'
}

# jiraTicketKey is set when TICKET_KEY is a synthetic dedup key (e.g. PR-200, CONFLICT-199).
# The orchestrator uses it to post the Jira metrics comment on the correct issue.
jira_key = os.environ.get('JIRA_TICKET_KEY', '').strip()
if jira_key:
    payload['jiraTicketKey'] = jira_key

# Include comment body written by Claude — orchestrator appends metrics and posts to Jira.
# Read via Python to avoid shell quoting issues with newlines and special characters.
comment_file = '/tmp/jira_comment.txt'
if os.path.exists(comment_file):
    with open(comment_file) as f:
        payload['commentBody'] = f.read()

data = base64.b64encode(json.dumps(payload).encode()).decode()
print(json.dumps({'messages': [{'data': data}]}))
")

  curl -sf -X POST \
    "https://pubsub.googleapis.com/v1/projects/${GCP_PROJECT_ID}/topics/${PUBSUB_TOPIC}:publish" \
    -H "Authorization: Bearer ${gcp_token}" \
    -H "Content-Type: application/json" \
    -d "$message" \
    && echo "Pub/Sub completion event published (status=$status)" \
    || echo "Warning: Failed to publish Pub/Sub completion event — orchestrator will recover on restart"
}
# Cloud Run sends SIGTERM when a task exceeds its timeout (default 1800s).
# Without an explicit TERM trap, bash may exit with code 0 (the last successful
# command's exit code), causing publish_completion to report status=success even
# though the worker was cancelled. Exiting with 143 (128 + SIGTERM) ensures the
# EXIT trap always sees a non-zero code on cancellation or interruption.
trap 'exit 143' TERM INT
# Register once — fires on every exit path, including set -e early exits.
trap 'publish_completion $?' EXIT

# Generate a GitHub App installation token for gh CLI.
# Judge jobs typically complete in under 5 minutes; the 1-hour token TTL covers this.
echo "Generating GitHub App installation token..."
GITHUB_TOKEN=$(python3 /home/agent/get-github-token.py)
if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: Failed to generate GitHub App installation token." >&2
  echo "Ensure GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, and GITHUB_APP_PRIVATE_KEY_BASE64 are set." >&2
  exit 1
fi
export GH_TOKEN="$GITHUB_TOKEN"
echo "GitHub App token generated successfully"

# Configure git credential store so gh CLI operations authenticate without prompting.
git config --global credential.helper store
echo "https://x-access-token:${GITHUB_TOKEN}@github.com" > ~/.git-credentials

cat > "/home/agent/.mcp.json" << EOF
{
  "mcpServers": {
    "atlassian": {
      "command": "mcp-atlassian",
      "env": {
        "JIRA_URL": "https://media-sage.atlassian.net",
        "JIRA_USERNAME": "${JIRA_EMAIL}",
        "JIRA_API_TOKEN": "${JIRA_API_TOKEN}",
        "CONFLUENCE_URL": "https://media-sage.atlassian.net/wiki",
        "CONFLUENCE_USERNAME": "${JIRA_EMAIL}",
        "CONFLUENCE_API_TOKEN": "${JIRA_API_TOKEN}"
      }
    }
  }
}
EOF

# Log the full prompt as a single Cloud Run log entry by emitting it as a JSON object.
# Cloud Run splits stdout on newlines — a bare printf/echo produces one entry per line of the prompt.
# Writing a single JSON line keeps the entire prompt in one entry regardless of embedded newlines.
python3 -c "import json, os; print(json.dumps({'message': '[worker] prompt', 'prompt': os.environ.get('PROMPT', '')}))"

# Run Claude Code — no exec so the trap can capture the exit code for Pub/Sub.
# --verbose is required when using --output-format=stream-json.
claude -p "$PROMPT" \
  --dangerously-skip-permissions \
  --output-format stream-json \
  --verbose
