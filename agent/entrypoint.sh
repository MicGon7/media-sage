#!/bin/bash
set -e

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-bot}"
git config --global user.email "${GITHUB_BOT_EMAIL}"

REPO_DIR="${AGENT_REPO_PATH:-/home/agent/media-sage}"
REPO_URL="https://${GITHUB_BOT_TOKEN}@github.com/MicGon7/media-sage.git"

if [ -d "$REPO_DIR/.git" ]; then
  echo "Repo exists, pulling latest..."
  git -C "$REPO_DIR" pull
else
  echo "Cloning repo to $REPO_DIR..."
  git clone "$REPO_URL" "$REPO_DIR"
fi

exec java $JAVA_OPTS -jar app.jar
