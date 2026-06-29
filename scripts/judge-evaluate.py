#!/usr/bin/env python3
"""
Reads judge-fetch.sh output from stdin, calls the Anthropic API directly,
posts the verdict to the PR, and writes /tmp/jira_comment.txt.

Required env vars: ANTHROPIC_AUTH_TOKEN, PR_NUMBER
Optional env vars: ANTHROPIC_BASE_URL, ANTHROPIC_MODEL, JIRA_TICKET_KEY, TICKET_KEY
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

fetch_output = sys.stdin.read()

pr_number = os.environ["PR_NUMBER"]
jira_key = os.environ.get("JIRA_TICKET_KEY") or os.environ.get("TICKET_KEY", "UNKNOWN")
auth_token = os.environ["ANTHROPIC_AUTH_TOKEN"]
base_url = os.environ.get("ANTHROPIC_BASE_URL", "https://api.anthropic.com").rstrip("/")
model = os.environ.get("ANTHROPIC_MODEL", "claude-sonnet-4")

SYSTEM_PROMPT = f"""\
You are a code review judge. Evaluate the pull request and return a plain-text verdict \
using EXACTLY the format below — no markdown bold, no extra sections.

AC compliance rules:
- ✅ Met — cite one specific line or file from the diff as evidence
- ❌ Not met — name exactly what is missing from the diff
- ⚠️ Partial — state what is present and what is missing
- Skip CI gate items (tests pass, detekt, PR targets main) — not diff-verifiable

Test coverage: ✅ if diff includes tests for new behavior | ❌ if new implementation added \
with no test changes | ⚠️ if test files changed but scope is unclear
Regression surface: from "Shared infra files" in the fetch output — ✅ if empty | \
⚠️ name each file and explain what it is shared with
PR description: ✅ if body describes what the diff does | ❌ if body claims changes not in \
the diff | ⚠️ if vague or omits significant changes
Overall: PASS (all AC ✅) | FAIL (any AC ❌) | PARTIAL (any ⚠️ or Jira AC unavailable)

Return exactly this format — nothing before or after:
🤖 Agent: Judge verdict for {jira_key}

Task: Judge verdict on PR #{pr_number}

AC compliance:
[one line per AC item: ✅/❌/⚠️ item — explanation]

Test coverage: ✅/❌/⚠️ [result]
Regression surface: ✅/⚠️ [result]
PR description: ✅/❌/⚠️ [result]

Overall: PASS / FAIL / PARTIAL"""

body = json.dumps({
    "model": model,
    "max_tokens": 1024,
    "system": SYSTEM_PROMPT,
    "messages": [{"role": "user", "content": fetch_output}],
})

start_ms = int(time.time() * 1000)

req = urllib.request.Request(
    f"{base_url}/v1/messages",
    data=body.encode(),
    headers={
        "x-api-key": auth_token,
        "Content-Type": "application/json",
        "anthropic-version": "2023-06-01",
        "User-Agent": "curl/7.88.1",
    },
    method="POST",
)

try:
    with urllib.request.urlopen(req) as resp:
        response = json.loads(resp.read())
except urllib.error.HTTPError as e:
    print(f"ERROR: Anthropic API error {e.code}: {e.read().decode()}", file=sys.stderr)
    sys.exit(1)

verdict = response["content"][0]["text"].strip()
duration_ms = int(time.time() * 1000) - start_ms

# Write Jira comment — entrypoint-common.sh appends metrics and posts it
with open("/tmp/jira_comment.txt", "w") as f:
    f.write(verdict)

# Write synthetic metrics so entrypoint-common.sh can report cost/duration in Pub/Sub
usage = response.get("usage", {})
input_tokens = usage.get("input_tokens", 0)
output_tokens = usage.get("output_tokens", 0)
cost_usd = (input_tokens * 3.0 + output_tokens * 15.0) / 1_000_000
with open("/tmp/claude-output.jsonl", "w") as f:
    f.write(json.dumps({
        "type": "result",
        "num_turns": 1,
        "total_cost_usd": round(cost_usd, 6),
        "duration_ms": duration_ms,
        "usage": {"input_tokens": input_tokens, "output_tokens": output_tokens},
    }) + "\n")

# Post PR review comment (informational only — not an approval)
pr_comment = (
    verdict.replace("🤖 Agent:", "🤖 **Agent:**", 1)
    + "\n\nThis verdict is informational. The human reviewer makes the final call."
)
subprocess.run(
    ["gh", "pr", "review", pr_number, "--comment", "--body", pr_comment],
    check=True,
)
