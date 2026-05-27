#!/bin/bash
set -e

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-worker}"
# GitHub App noreply email — deterministic from the App ID, no env var needed
git config --global user.email "${GITHUB_APP_ID}+media-sage-worker[bot]@users.noreply.github.com"

# Generate a GitHub App installation token for git and gh CLI authentication.
# The token is valid for 1 hour — sufficient for orchestrator startup operations.
# Runtime gh calls (e.g. postInlineCommentReply) use GitHubAppTokenService in the
# Kotlin code to refresh the token automatically before each invocation.
echo "Generating GitHub App installation token..."
GITHUB_TOKEN=$(python3 /home/agent/get-github-token.py)
if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: Failed to generate GitHub App installation token." >&2
  echo "Ensure GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, and GITHUB_APP_PRIVATE_KEY_BASE64 are set." >&2
  exit 1
fi
export GH_TOKEN="$GITHUB_TOKEN"

REPO_DIR="${AGENT_REPO_PATH:-/home/agent/media-sage}"
# GitHub App installation tokens use x-access-token as the username in clone URLs
REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/michael-gonzalez-dev/media-sage.git"

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
