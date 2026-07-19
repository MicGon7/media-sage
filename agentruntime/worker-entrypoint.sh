#!/bin/bash
set -eo pipefail

# shellcheck source=entrypoint-common.sh
source "$(dirname "$0")/entrypoint-common.sh"

REPO_DIR="/home/agent/${GITHUB_REPO}"
# GitHub App installation tokens use x-access-token as the username in clone URLs
REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"

echo "Cloning repo..."
git clone --depth=1 "$REPO_URL" "$REPO_DIR"
cd "$REPO_DIR"

# Log the dispatched job type and identifiers as a single Cloud Run log entry.
python3 -c "import json, os; print(json.dumps({'message': '[worker] job dispatched', 'jobType': os.environ.get('JOB_TYPE', ''), 'ticketKey': os.environ.get('TICKET_KEY', ''), 'prNumber': os.environ.get('PR_NUMBER', '')}))"

# Run Claude Code — no exec so the trap can capture the exit code for Pub/Sub.
# --verbose is required when using --output-format=stream-json.
# Tee to capture stream-json output for metrics parsing in publish_completion.
# ANTHROPIC_MODEL and WORKER_EFFORT are set on the worker Cloud Run Job by build-worker-image.yml.
# ANTHROPIC_MODEL is the standard var Claude Code reads for model selection; WORKER_EFFORT is passed
# explicitly as --effort (Claude Code does not read it from the environment). The literal fallbacks
# below are the safety defaults — keep the model in sync with pipelineCore's DEFAULT_CLAUDE_MODEL,
# and the effort default at "high" (Sonnet's default) so an unset var leaves runs unchanged.
# Override either per comparison run via `gcloud run jobs update ... --update-env-vars`.
claude -p "/$JOB_TYPE" \
  --model "${ANTHROPIC_MODEL:-claude-sonnet-5}" \
  --effort "${WORKER_EFFORT:-high}" \
  --dangerously-skip-permissions \
  --output-format stream-json \
  --verbose \
  --append-system-prompt "Job context (confirmed set by entrypoint — do not verify): TICKET_KEY=${TICKET_KEY:-} JOB_TYPE=${JOB_TYPE:-} PR_NUMBER=${PR_NUMBER:-}" \
  | tee /tmp/claude-output.jsonl
