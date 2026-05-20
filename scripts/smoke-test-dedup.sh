#!/usr/bin/env bash
# Smoke test for MS-179 persistent job dedup and recovery.
#
# Tests all dedup cases against a live agent + Supabase DB:
#   Case 1: RUNNING job  → duplicate webhook skipped (no new row)
#   Case 2: COMPLETED    → repeated webhook skipped  (no new row)
#   Case 3: FAILED       → re-dispatch allowed       (new PENDING row)
#   Case 4: INTERRUPTED  → re-dispatch allowed       (new PENDING row)
#
# Prerequisites:
#   brew install postgresql   # for psql
#   brew install jq           # for JSON parsing
#
# Usage:
#   export SUPABASE_DB_URL="postgresql://postgres.<ref>:<password>@<host>:5432/postgres"
#   export AGENT_URL="https://<railway-agent-url>"
#   export JIRA_BOT_ACCOUNT_ID="<bot-account-id>"
#   ./scripts/smoke-test-dedup.sh [--dry-run]
#
# --dry-run  Cases 3 and 4 verify PENDING row insertion without triggering a real
#            Cloud Run dispatch. Use this for local testing to avoid token waste and
#            30-minute polling loops in the orchestrator.
#
# For local dev (ngrok):
#   export AGENT_URL="https://<ngrok-url>"

set -euo pipefail

DRY_RUN=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        *) echo "Unknown argument: $arg"; exit 1 ;;
    esac
done

: "${SUPABASE_DB_URL:?Set SUPABASE_DB_URL to the Supabase Postgres connection string}"
: "${AGENT_URL:?Set AGENT_URL to the agent base URL (no trailing slash)}"
: "${JIRA_BOT_ACCOUNT_ID:?Set JIRA_BOT_ACCOUNT_ID to the Jira bot account ID}"

TICKET_KEY="MS-SMOKE-DEDUP-$$"  # unique per run to avoid cross-run interference
WEBHOOK_URL="$AGENT_URL/webhook/jira"

pass() { echo "✅  $1"; }
fail() { echo "❌  $1"; exit 1; }

# ── Helpers ────────────────────────────────────────────────────────────────────

psql_cmd() {
    psql "$SUPABASE_DB_URL" -tA -c "$1" 2>/dev/null
}

row_count() {
    psql_cmd "SELECT count(*) FROM jobs WHERE ticket_key = '$TICKET_KEY';"
}

insert_job() {
    local status="$1"
    psql_cmd "INSERT INTO jobs (ticket_key, prompt, status, created_at) \
              VALUES ('$TICKET_KEY', 'smoke test', '$status', now());" > /dev/null
}

fire_webhook() {
    local use_dry_run="${1:-false}"
    local curl_args=(-s -o /dev/null -w "%{http_code}" -X POST "$WEBHOOK_URL" \
        -H "Content-Type: application/json")
    if [ "$use_dry_run" = "true" ]; then
        curl_args+=(-H "X-Dry-Run: true")
    fi
    local status_code
    status_code=$(curl "${curl_args[@]}" -d "{
              \"webhookEvent\": \"jira:issue_updated\",
              \"issue\": {
                \"key\": \"$TICKET_KEY\",
                \"fields\": {
                  \"assignee\": { \"accountId\": \"$JIRA_BOT_ACCOUNT_ID\" },
                  \"status\": { \"name\": \"In Progress\" },
                  \"summary\": \"Smoke test — $TICKET_KEY\",
                  \"description\": null
                }
              }
            }")
    echo "$status_code"
}

cleanup() {
    psql_cmd "DELETE FROM jobs WHERE ticket_key = '$TICKET_KEY';" > /dev/null
}

# ── Run ────────────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════"
echo " MS-179 Dedup Smoke Test  —  ticket: $TICKET_KEY"
if [ "$DRY_RUN" = "true" ]; then
    echo " Mode: DRY-RUN (no Cloud Run dispatch)"
fi
echo "═══════════════════════════════════════════════"
echo ""

trap cleanup EXIT  # always clean up test rows on exit

# ── Case 1: RUNNING → skip ─────────────────────────────────────────────────────

echo "Case 1: RUNNING job → webhook should be ignored"
cleanup
insert_job "RUNNING"
before=$(row_count)
http_code=$(fire_webhook)
[ "$http_code" = "200" ] || fail "Webhook returned $http_code (expected 200)"
sleep 2
after=$(row_count)
[ "$before" -eq "$after" ] \
    && pass "No new row inserted (count stayed at $before)" \
    || fail "Row count changed from $before to $after — duplicate dispatch detected"

echo ""

# ── Case 2: COMPLETED → skip ───────────────────────────────────────────────────

echo "Case 2: COMPLETED job → webhook should be ignored"
cleanup
insert_job "COMPLETED"
before=$(row_count)
http_code=$(fire_webhook)
[ "$http_code" = "200" ] || fail "Webhook returned $http_code (expected 200)"
sleep 2
after=$(row_count)
[ "$before" -eq "$after" ] \
    && pass "No new row inserted (count stayed at $before)" \
    || fail "Row count changed from $before to $after — completed ticket was re-dispatched"

echo ""

# ── Case 3: FAILED → re-dispatch ──────────────────────────────────────────────

echo "Case 3: FAILED job → webhook should create new PENDING row"
cleanup
insert_job "FAILED"
before=$(row_count)
http_code=$(fire_webhook "$DRY_RUN")
[ "$http_code" = "200" ] || fail "Webhook returned $http_code (expected 200)"
sleep 2
after=$(row_count)
[ "$after" -gt "$before" ] \
    && pass "New row inserted (count went from $before to $after)" \
    || fail "Row count unchanged at $before — failed job was not re-dispatched"

echo ""

# ── Case 4: INTERRUPTED → re-dispatch ─────────────────────────────────────────

echo "Case 4: INTERRUPTED job → webhook should create new PENDING row"
cleanup
insert_job "INTERRUPTED"
before=$(row_count)
http_code=$(fire_webhook "$DRY_RUN")
[ "$http_code" = "200" ] || fail "Webhook returned $http_code (expected 200)"
sleep 2
after=$(row_count)
[ "$after" -gt "$before" ] \
    && pass "New row inserted (count went from $before to $after)" \
    || fail "Row count unchanged at $before — interrupted job was not re-dispatched"

echo ""
echo "═══════════════════════════════════════════════"
echo " All 4 dedup cases passed ✅"
echo "═══════════════════════════════════════════════"
echo ""
if [ "$DRY_RUN" = "true" ]; then
    echo "Dry-run mode: Cases 3 and 4 verified PENDING row insertion."
    echo "Cloud Run dispatch was skipped — no polling, no token usage."
else
    echo "Note: Cases 3 and 4 insert a PENDING row and attempt Cloud Run dispatch."
    echo "Use --dry-run for local testing to avoid token waste and long polling loops."
fi
echo "Check the jobs table to see the final status:"
echo "  psql \"\$SUPABASE_DB_URL\" -c \"SELECT ticket_key, status, created_at FROM jobs ORDER BY created_at DESC LIMIT 10;\""
