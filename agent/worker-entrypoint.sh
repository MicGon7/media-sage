#!/bin/bash
set -e

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-bot}"
git config --global user.email "${GITHUB_BOT_EMAIL}"
export GH_TOKEN="${GITHUB_BOT_TOKEN}"

REPO_DIR="/home/agent/media-sage"
REPO_URL="https://${GITHUB_BOT_TOKEN}@github.com/MicGon7/media-sage.git"

echo "Cloning repo..."
git clone "$REPO_URL" "$REPO_DIR"
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

exec claude -p "$PROMPT" \
  --dangerously-skip-permissions \
  --output-format stream-json \
  --verbose
