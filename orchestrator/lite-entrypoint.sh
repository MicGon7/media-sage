#!/bin/bash
set -eo pipefail

# shellcheck source=entrypoint-common.sh
source "$(dirname "$0")/entrypoint-common.sh"

# GH_REPO is required by gh CLI commands when no git repo is cloned.
export GH_REPO="$GITHUB_OWNER/$GITHUB_REPO"

# Log the dispatched job type and identifiers as a single Cloud Run log entry.
python3 -c "import json, os; print(json.dumps({'message': '[judge] job dispatched', 'jobType': os.environ.get('JOB_TYPE', ''), 'ticketKey': os.environ.get('TICKET_KEY', ''), 'prNumber': os.environ.get('PR_NUMBER', '')}))"

# Fetch all judge inputs, pipe directly to the evaluator.
# judge-fetch.sh collects PR metadata + Jira AC + diff; judge-evaluate.py calls
# the Anthropic API, posts the PR review comment, and writes /tmp/jira_comment.txt.
./scripts/judge-fetch.sh "${PR_NUMBER:?ERROR: PR_NUMBER is required}" \
  | python3 ./scripts/judge-evaluate.py
