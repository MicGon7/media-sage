#!/bin/bash
set -eo pipefail

# shellcheck source=entrypoint-common.sh
source "$(dirname "$0")/entrypoint-common.sh"

# GH_REPO is required by gh CLI commands when no git repo is cloned.
export GH_REPO="$GITHUB_OWNER/$GITHUB_REPO"

# Log the dispatched job type and identifiers as a single Cloud Run log entry.
python3 -c "import json, os; print(json.dumps({'message': '[judge] job dispatched', 'jobType': os.environ.get('JOB_TYPE', ''), 'ticketKey': os.environ.get('TICKET_KEY', ''), 'prNumber': os.environ.get('PR_NUMBER', '')}))"

# Run Claude Code — no exec so the trap can capture the exit code for Pub/Sub.
# --verbose is required when using --output-format=stream-json.
# Tee to capture stream-json output for metrics parsing in publish_completion.
claude -p "/$JOB_TYPE" \
  --dangerously-skip-permissions \
  --output-format stream-json \
  --verbose \
  --append-system-prompt "Job context (confirmed set by entrypoint — do not verify): TICKET_KEY=${TICKET_KEY:-} JIRA_TICKET_KEY=${JIRA_TICKET_KEY:-} JOB_TYPE=${JOB_TYPE:-} PR_NUMBER=${PR_NUMBER:-}" \
  | tee /tmp/claude-output.jsonl
