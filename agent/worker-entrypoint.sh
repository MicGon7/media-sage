#!/bin/bash
set -e

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-worker}"
# GitHub App noreply email — deterministic from the App ID, no env var needed
git config --global user.email "${GITHUB_APP_ID}+media-sage-worker[bot]@users.noreply.github.com"

# Generate a GitHub App installation token for git clone and gh CLI.
# Workers typically run for 10-30 minutes; the 1-hour token TTL covers this.
# If a worker runs longer than 1 hour, gh commands will begin failing — this is
# accepted as an edge case and will be addressed with credential-helper support if needed.
echo "Generating GitHub App installation token..."
GITHUB_TOKEN=$(python3 /home/agent/get-github-token.py)
if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: Failed to generate GitHub App installation token." >&2
  echo "Ensure GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, and GITHUB_APP_PRIVATE_KEY_BASE64 are set." >&2
  exit 1
fi
export GH_TOKEN="$GITHUB_TOKEN"

REPO_DIR="/home/agent/media-sage"
# GitHub App installation tokens use x-access-token as the username in clone URLs
REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/MicGon7/media-sage.git"

echo "Cloning repo..."
git clone --depth=1 "$REPO_URL" "$REPO_DIR"
cd "$REPO_DIR"

cat > "$REPO_DIR/.mcp.json" << EOF
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

# Run Claude Code — no exec so we can capture the exit code and signal completion via Pub/Sub
claude -p "$PROMPT" \
  --dangerously-skip-permissions \
  --output-format stream-json \
  --verbose
CLAUDE_EXIT=$?

# Publish completion event to Pub/Sub so the orchestrator is notified immediately.
# Uses the GCP metadata server for auth — always available inside Cloud Run.
# A publish failure is logged as a warning and does not fail the job; the orchestrator's
# recoverInterruptedJobs() handles the rare case where the event is lost.
if [ -n "$PUBSUB_TOPIC" ] && [ -n "$GCP_PROJECT_ID" ]; then
  TOKEN=$(curl -sf \
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" \
    -H "Metadata-Flavor: Google" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

  STATUS=$([ "$CLAUDE_EXIT" -eq 0 ] && echo "success" || echo "failure")

  MESSAGE=$(python3 -c "
import json, base64
payload = json.dumps({
  'ticketKey': '${TICKET_KEY}',
  'executionName': '${CLOUD_RUN_EXECUTION}',
  'status': '${STATUS}'
})
data = base64.b64encode(payload.encode()).decode()
print(json.dumps({'messages': [{'data': data}]}))
")

  curl -sf -X POST \
    "https://pubsub.googleapis.com/v1/projects/${GCP_PROJECT_ID}/topics/${PUBSUB_TOPIC}:publish" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$MESSAGE" \
    && echo "Pub/Sub completion event published (status=$STATUS)" \
    || echo "Warning: Failed to publish Pub/Sub completion event — orchestrator will recover on restart"
else
  echo "PUBSUB_TOPIC or GCP_PROJECT_ID not set — skipping Pub/Sub notification"
fi

exit $CLAUDE_EXIT
