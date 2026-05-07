#!/bin/bash
set -e

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-bot}"
git config --global user.email "${GITHUB_BOT_EMAIL}"
export GH_TOKEN="${GITHUB_BOT_TOKEN}"

REPO_DIR="${AGENT_REPO_PATH:-/home/agent/media-sage}"
REPO_URL="https://${GITHUB_BOT_TOKEN}@github.com/MicGon7/media-sage.git"

if [ -d "$REPO_DIR/.git" ]; then
  echo "Repo exists, pulling latest..."
  git -C "$REPO_DIR" pull
else
  echo "Cloning repo to $REPO_DIR..."
  git clone "$REPO_URL" "$REPO_DIR"
fi

# Write MCP config with API token auth — overrides the repo's OAuth SSE config
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

exec java -jar app.jar
