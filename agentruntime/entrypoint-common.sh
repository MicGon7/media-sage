# Sourced by worker-entrypoint.sh and lite-entrypoint.sh.
# Do not run directly. Caller must set -eo pipefail before sourcing.

git config --global user.name "${GITHUB_BOT_NAME:-media-sage-worker}"
# GitHub App noreply email — deterministic from the App ID, no env var needed
git config --global user.email "${GITHUB_APP_ID}+media-sage-worker[bot]@users.noreply.github.com"

# Publish a Pub/Sub completion event on any exit — success, failure, or early error
# (e.g. token generation failure, set -e killing the script mid-run).
# Uses the GCP metadata server for auth — always available in Cloud Run, independent
# of whether GITHUB_TOKEN was successfully generated.
# A publish failure is logged as a warning and does not affect the exit code; the
# orchestrator's recoverInterruptedJobs() handles the rare case where the event is lost.
publish_completion() {
  local exit_code=$1

  # Parse metrics from stream-json output and append to the Jira comment file.
  local metrics
  metrics=$(python3 -c "
import json
for line in open('/tmp/claude-output.jsonl'):
    try:
        e = json.loads(line)
        if e.get('type') == 'result':
            t = e.get('num_turns', '?')
            c = e.get('total_cost_usd', 0)
            d_ms = e.get('duration_ms', 0)
            d_m = d_ms // 60000
            d_s = (d_ms % 60000) // 1000
            d_str = f'{d_m}m {d_s:02d}s'
            print(f'{t} turns | \${c:.4f} | {d_str}')
    except: pass
" 2>/dev/null || echo "metrics unavailable")
  if [ -f /tmp/jira_comment.txt ]; then
    printf '\n---\n%s\n' "$metrics" >> /tmp/jira_comment.txt
  fi

  # Post Jira comment — job owns the comment end-to-end.
  # Credentials were unset from the exported environment before Claude ran; they are
  # Bot credentials are exported and available throughout the process.
  # so Claude's subprocess could never access them.
  local effective_jira_key="${JIRA_TICKET_KEY:-$TICKET_KEY}"
  if [ -f /tmp/jira_comment_posted ]; then
    echo "Jira comment already posted — skipping duplicate"
  elif [ -z "$JIRA_BOT_EMAIL" ] || [ -z "$JIRA_BOT_API_TOKEN" ]; then
    echo "Warning: JIRA_BOT_EMAIL or JIRA_BOT_API_TOKEN not set — skipping Jira comment to avoid posting as personal account"
  elif [ -f /tmp/jira_comment.txt ] && [ -n "$effective_jira_key" ]; then
    python3 - "$effective_jira_key" "$JIRA_BOT_EMAIL" "$JIRA_BOT_API_TOKEN" << 'PYEOF' || echo "Warning: Failed to post Jira comment"
import json, subprocess, sys
ticket_key = sys.argv[1]
jira_user = sys.argv[2]
jira_token = sys.argv[3]
comment_text = open('/tmp/jira_comment.txt').read()
try:
    pr_url = open('/tmp/worker_pr_url.txt').read().strip()
    comment_text = comment_text.replace('{pr_url}', pr_url)
except FileNotFoundError:
    pass
body = json.dumps({
    'body': {
        'type': 'doc', 'version': 1,
        'content': [{'type': 'paragraph', 'content': [{'type': 'text', 'text': comment_text}]}]
    }
})
result = subprocess.run(
    ['curl', '-sf', '-X', 'POST',
     '-u', f"{jira_user}:{jira_token}",
     '-H', 'Content-Type: application/json',
     '-d', body,
     f'https://media-sage.atlassian.net/rest/api/3/issue/{ticket_key}/comment'],
    capture_output=True, text=True
)
if result.returncode == 0:
    print('Jira comment posted')
else:
    print(f'Warning: Jira comment post failed: {result.stderr}')
PYEOF
    touch /tmp/jira_comment_posted
  fi

  # Persist raw JSONL transcript to Supabase for advisor and feedback scanning.
  # Runs unconditionally on every job type (worker, judge, etc.) so all sessions
  # are available for advisor analysis and decision scoring.
  if [ -f /tmp/claude-output.jsonl ]; then
    python3 - << 'PYEOF'
import json, os, subprocess, sys

job_id = os.environ.get('JOB_ID', '')
rest_url = os.environ.get('SUPABASE_REST_URL', '').rstrip('/')
svc_key = os.environ.get('SUPABASE_SERVICE_ROLE_KEY', '')

if not (rest_url and svc_key and job_id):
    print("Warning: SUPABASE_REST_URL, SUPABASE_SERVICE_ROLE_KEY, or JOB_ID not set — skipping transcript upload", file=sys.stderr)
    sys.exit(0)

try:
    raw_jsonl = open('/tmp/claude-output.jsonl').read()
except Exception as e:
    print(f"Warning: could not read transcript: {e}", file=sys.stderr)
    sys.exit(0)

body = json.dumps({'job_id': job_id, 'content': raw_jsonl})
result = subprocess.run(
    ['curl', '-sf', '-X', 'POST',
     f'{rest_url}/transcripts',
     '-H', f'apikey: {svc_key}',
     '-H', f'Authorization: Bearer {svc_key}',
     '-H', 'Content-Type: application/json',
     '-H', 'Prefer: return=minimal',
     '-d', body],
    capture_output=True, text=True
)
if result.returncode == 0:
    print("Raw JSONL transcript persisted to Supabase")
else:
    print(f"Warning: Failed to persist transcript to Supabase: {result.stderr}", file=sys.stderr)
PYEOF
  fi

  if [ -z "$PUBSUB_TOPIC" ] || [ -z "$GCP_PROJECT_ID" ]; then
    echo "PUBSUB_TOPIC or GCP_PROJECT_ID not set — skipping Pub/Sub notification"
    return
  fi

  local status
  status=$([ "$exit_code" -eq 0 ] && echo "success" || echo "failure")

  local gcp_token
  gcp_token=$(curl -sf \
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" \
    -H "Metadata-Flavor: Google" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])") || {
    echo "Warning: Failed to fetch GCP metadata token — cannot publish without auth; orchestrator will recover on restart"
    return
  }

  local message
  message=$(python3 -c "
import json, base64, os, re

payload = {
  'ticketKey': '${TICKET_KEY}',
  'executionName': '${CLOUD_RUN_EXECUTION}',
  'status': '$status'
}

# jiraTicketKey is set when TICKET_KEY is a synthetic dedup key (e.g. PR-200, CONFLICT-199).
jira_key = os.environ.get('JIRA_TICKET_KEY', '').strip()
if jira_key:
    payload['jiraTicketKey'] = jira_key

# Include PR number so the judge can skip the gh pr list discovery turn.
try:
    pr_url = open('/tmp/worker_pr_url.txt').read().strip()
    m = re.search(r'/pull/(\d+)', pr_url)
    if m:
        payload['prNumber'] = int(m.group(1))
except Exception:
    pass

# failedGate: on a failed run the worker writes the quality gate that blocked it
# (e.g. compile, tests, detekt, ci) to /tmp/failed_gate.txt. The orchestrator persists
# it to jobs.failed_gate for failure attribution (MS-386). Only sent on failure.
if '$status' == 'failure':
    try:
        gate = open('/tmp/failed_gate.txt').read().strip()
        if gate:
            payload['failedGate'] = gate
    except Exception:
        pass

# Worker metrics from the Claude Code result event (MS-412).
# Embedded here so the orchestrator can persist them without a Cloud Logging fetch.
try:
    for line in open('/tmp/claude-output.jsonl'):
        try:
            e = json.loads(line)
            if e.get('type') == 'result':
                payload['numTurns'] = e.get('num_turns', 0)
                payload['totalCostUsd'] = e.get('total_cost_usd', 0.0)
                payload['durationMs'] = e.get('duration_ms', 0)
                usage = e.get('usage') or {}
                model_usage = e.get('modelUsage') or {}
                def resolve_token(usage_key, model_key):
                    val = usage.get(usage_key, 0)
                    if val and val > 0:
                        return val
                    return sum((v or {}).get(model_key, 0) for v in model_usage.values())
                payload['inputTokens'] = resolve_token('input_tokens', 'inputTokens')
                payload['outputTokens'] = resolve_token('output_tokens', 'outputTokens')
                payload['cacheReadTokens'] = resolve_token('cache_read_input_tokens', 'cacheReadInputTokens')
                payload['cacheCreationTokens'] = resolve_token('cache_creation_input_tokens', 'cacheCreationInputTokens')
                if model_usage:
                    payload['modelVersion'] = os.environ.get('CLAUDE_MODEL', 'claude-sonnet-4')
                break
        except Exception:
            pass
except Exception:
    pass

data = base64.b64encode(json.dumps(payload).encode()).decode()
print(json.dumps({'messages': [{'data': data}]}))
")

  curl -sf -X POST \
    "https://pubsub.googleapis.com/v1/projects/${GCP_PROJECT_ID}/topics/${PUBSUB_TOPIC}:publish" \
    -H "Authorization: Bearer ${gcp_token}" \
    -H "Content-Type: application/json" \
    -d "$message" \
    && echo "Pub/Sub completion event published (status=$status)" \
    || echo "Warning: Failed to publish Pub/Sub completion event — orchestrator will recover on restart"
}

# Cloud Run sends SIGTERM when a task exceeds its timeout (default 1800s).
# Without an explicit TERM trap, bash may exit with code 0 (the last successful
# command's exit code), causing publish_completion to report status=success even
# though the job was cancelled. Exiting with 143 (128 + SIGTERM) ensures the
# EXIT trap always sees a non-zero code on cancellation or interruption.
trap 'exit 143' TERM INT
# Register once — fires on every exit path, including set -e early exits.
trap 'publish_completion $?' EXIT

# Generate a GitHub App installation token for git clone and gh CLI.
# The 1-hour TTL covers typical job durations (workers: 10-30 min, judge/comment: <5 min).
echo "Generating GitHub App installation token..."
GITHUB_TOKEN=$(python3 /home/agent/get-github-token.py)
if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: Failed to generate GitHub App installation token." >&2
  echo "Ensure GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, and GITHUB_APP_PRIVATE_KEY_BASE64 are set." >&2
  exit 1
fi
export GH_TOKEN="$GITHUB_TOKEN"
echo "GitHub App token generated successfully"

# Configure git credential store so all git and gh CLI operations authenticate
# without prompting. Cloud Run has no TTY — credentials must be pre-configured.
git config --global credential.helper store
echo "https://x-access-token:${GITHUB_TOKEN}@github.com" > ~/.git-credentials

if [ -z "$GITHUB_OWNER" ] || [ -z "$GITHUB_REPO" ]; then
  echo "ERROR: GITHUB_OWNER and GITHUB_REPO must be set." >&2
  exit 1
fi

# JIRA_BOT_EMAIL and JIRA_BOT_API_TOKEN remain exported — the bot is the worker's
# identity for all Jira writes (transitions, AC checkboxes, comments).
