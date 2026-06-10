#!/usr/bin/env bash
# Validates that all Cloud Run jobs have the required env vars and secrets.
# Run this before triggering a smoke test to catch missing config early.
#
# Usage:
#   ./scripts/validate-job-configs.sh
#
# To fix missing vars, use --update-env-vars (plain values) or --update-secrets (secrets).
# NEVER use --set-env-vars — it replaces ALL existing plain env vars on the job.
#
# Exit codes:
#   0 — all jobs pass
#   1 — one or more jobs are missing required vars

set -euo pipefail

REGION="us-central1"
PROJECT="media-sage-agent"

# Required env vars per job type.
# Format: "JOB_NAME:VAR1,VAR2,..."
REQUIREMENTS=(
  "media-sage-agent-worker:ANTHROPIC_AUTH_TOKEN,ANTHROPIC_BASE_URL,ANTHROPIC_MODEL,GCP_PROJECT_ID,GITHUB_APP_ID,GITHUB_APP_INSTALLATION_ID,GITHUB_APP_PRIVATE_KEY_BASE64,GITHUB_BOT_LOGIN,GITHUB_BOT_NAME,GITHUB_OWNER,GITHUB_REPO,JIRA_API_TOKEN,JIRA_BOT_API_TOKEN,JIRA_BOT_EMAIL,JIRA_EMAIL,PUBSUB_TOPIC"
  "media-sage-agent-judge:ANTHROPIC_AUTH_TOKEN,ANTHROPIC_BASE_URL,ANTHROPIC_MODEL,GCP_PROJECT_ID,GITHUB_APP_ID,GITHUB_APP_INSTALLATION_ID,GITHUB_APP_PRIVATE_KEY_BASE64,GITHUB_OWNER,GITHUB_REPO,JIRA_BOT_API_TOKEN,JIRA_BOT_EMAIL,PUBSUB_TOPIC"
  "media-sage-agent-comment:ANTHROPIC_AUTH_TOKEN,ANTHROPIC_BASE_URL,ANTHROPIC_MODEL,GCP_PROJECT_ID,GITHUB_APP_ID,GITHUB_APP_INSTALLATION_ID,GITHUB_APP_PRIVATE_KEY_BASE64,GITHUB_OWNER,GITHUB_REPO,JIRA_BOT_API_TOKEN,JIRA_BOT_EMAIL,PUBSUB_TOPIC"
)

PASS=true

for entry in "${REQUIREMENTS[@]}"; do
  JOB="${entry%%:*}"
  REQUIRED_VARS="${entry##*:}"

  echo ""
  echo "Checking $JOB..."

  PRESENT=$(gcloud run jobs describe "$JOB" \
    --region="$REGION" --project="$PROJECT" \
    --format='value(spec.template.spec.template.spec.containers[0].env[].name)' 2>&1 \
    | tr ';' '\n' | sort)

  MISSING=()
  IFS=',' read -ra VARS <<< "$REQUIRED_VARS"
  for var in "${VARS[@]}"; do
    if ! echo "$PRESENT" | grep -qx "$var"; then
      MISSING+=("$var")
    fi
  done

  if [ ${#MISSING[@]} -eq 0 ]; then
    echo "  ✅ All required vars present"
  else
    echo "  ❌ Missing: ${MISSING[*]}"
    PASS=false
  fi
done

echo ""
if [ "$PASS" = true ]; then
  echo "✅ All jobs are correctly configured."
  exit 0
else
  echo "❌ One or more jobs are missing required env vars — fix before running a smoke test."
  exit 1
fi
