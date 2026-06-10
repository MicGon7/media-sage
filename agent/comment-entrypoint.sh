#!/bin/bash
set -eo pipefail

# shellcheck source=entrypoint-common.sh
source "$(dirname "$0")/entrypoint-common.sh"

# GH_REPO is required by gh CLI commands when no git repo is cloned.
export GH_REPO="$GITHUB_OWNER/$GITHUB_REPO"

# Log the full prompt as a single Cloud Run log entry by emitting it as a JSON object.
# Cloud Run splits stdout on newlines — a bare printf/echo produces one entry per line of the prompt.
# Writing a single JSON line keeps the entire prompt in one entry regardless of embedded newlines.
python3 -c "import json, os; print(json.dumps({'message': '[worker] prompt', 'prompt': os.environ.get('PROMPT', '')}))"

# Run Claude Code — no exec so the trap can capture the exit code for Pub/Sub.
# --verbose is required when using --output-format=stream-json.
# Tee to capture stream-json output for metrics parsing in publish_completion.
claude -p "$PROMPT" \
  --dangerously-skip-permissions \
  --output-format stream-json \
  --verbose | tee /tmp/claude-output.jsonl
