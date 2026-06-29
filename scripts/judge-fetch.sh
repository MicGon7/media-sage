#!/usr/bin/env bash
# Fetches all judge inputs in a single turn and prints structured output to stdout.
# The judge reads from this tool result — no file reads needed in the next turn.
#
# Usage: ./scripts/judge-fetch.sh PR_NUMBER
# Requires: JIRA_BOT_EMAIL, JIRA_BOT_API_TOKEN

set -euo pipefail

PR_NUMBER="${1:?ERROR: PR_NUMBER is required}"

if [ -z "${JIRA_BOT_EMAIL:-}" ] || [ -z "${JIRA_BOT_API_TOKEN:-}" ]; then
    echo "ERROR: JIRA_BOT_EMAIL and JIRA_BOT_API_TOKEN must be set" >&2
    exit 1
fi

echo "═══════════════════════════════════════════"
echo " judge-fetch  —  PR #${PR_NUMBER}"
echo "═══════════════════════════════════════════"

# PR metadata
gh pr view "$PR_NUMBER" --json title,body,headRefName,baseRefName > /tmp/judge_pr.json

# Extract Jira ticket key from PR body or branch name
JIRA_KEY=$(python3 -c "
import json, re, sys
d = json.load(open('/tmp/judge_pr.json'))
text = d.get('body', '') + ' ' + d.get('headRefName', '')
m = re.search(r'MS-\d+', text)
print(m.group(0) if m else '')
")

if [ -z "$JIRA_KEY" ]; then
    echo "ERROR: Could not extract MS-NNNN ticket key from PR #${PR_NUMBER} body or branch" >&2
    exit 1
fi
echo "Jira key: $JIRA_KEY"

# Jira ticket: summary + description only (AC lives in description)
curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
    "https://media-sage.atlassian.net/rest/api/3/issue/${JIRA_KEY}?fields=summary,description" \
    > /tmp/judge_jira.json

# PR diff
gh pr diff "$PR_NUMBER" > /tmp/judge_diff.txt

# Parse and print structured output for the judge to evaluate from
python3 - "$JIRA_KEY" "$PR_NUMBER" << 'PYEOF'
import json, re, sys

jira_key = sys.argv[1]
pr_number = sys.argv[2]

pr_data = json.load(open('/tmp/judge_pr.json'))
jira_data = json.load(open('/tmp/judge_jira.json'))
diff_text = open('/tmp/judge_diff.txt').read()

def extract_adf(node):
    if not node:
        return ""
    t = node.get("type", "")
    if t == "text":
        return node.get("text", "")
    if t == "hardBreak":
        return "\n"
    if t in ("paragraph", "heading", "listItem"):
        return "".join(extract_adf(c) for c in node.get("content", [])) + "\n"
    return "".join(extract_adf(c) for c in node.get("content", []))

fields = jira_data.get("fields", {})
summary = fields.get("summary", "")
full_desc = extract_adf(fields.get("description")).strip()

# Extract Acceptance Criteria section
ac_text = ""
split = re.split(r"(?m)^#{1,3}\s+Acceptance Criteria", full_desc)
if len(split) >= 2:
    next_section = re.split(r"(?m)^#{1,3}\s+\w", split[1])
    ac_text = next_section[0].strip()

# Diff analysis: changed files, test presence, shared infra signals
changed_files = re.findall(r'^\+\+\+ b/(.+)$', diff_text, re.MULTILINE)
test_files = [f for f in changed_files if re.search(r'[Tt]est|/test/', f)]
shared_patterns = r'(repository|Repository|Database|database|Module\.kt|di/|mapper|Mapper|Entity\.kt|Dao\.kt|Api\.kt|Config\.kt)'
shared_files = [f for f in changed_files if re.search(shared_patterns, f) and f not in test_files]
added = len([l for l in diff_text.splitlines() if l.startswith('+') and not l.startswith('+++')])
removed = len([l for l in diff_text.splitlines() if l.startswith('-') and not l.startswith('---')])

print(f"JIRA_KEY={jira_key}")
print(f"PR_NUMBER={pr_number}")
print()
print("━━━ PR METADATA ━━━")
print(f"Title:  {pr_data.get('title', '')}")
print(f"Branch: {pr_data.get('headRefName', '')} → {pr_data.get('baseRefName', '')}")
print(f"Diff size: +{added} -{removed} lines across {len(changed_files)} file(s)")
print()
print("━━━ PR BODY ━━━")
print(pr_data.get('body', '(empty)'))
print()
print(f"━━━ JIRA AC ({jira_key}: {summary}) ━━━")
print(ac_text if ac_text else "(no Acceptance Criteria section found)")
print()
print("━━━ DIFF SIGNALS ━━━")
print(f"Test files in diff:                    {test_files if test_files else 'none'}")
print(f"Shared infra files (regression risk):  {shared_files if shared_files else 'none'}")
print(f"All changed files:                     {changed_files}")
print()
print("━━━ PR DIFF ━━━")
print(diff_text)
PYEOF
