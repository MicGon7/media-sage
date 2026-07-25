#!/usr/bin/env bash
# Fetches a Jira ticket and extracts its summary, description, and acceptance criteria.
#
# Usage:
#   ./scripts/worker-fetch-ticket.sh TICKET_KEY
#
# Output:
#   /tmp/worker_ticket.env — exports TICKET_SUMMARY, TICKET_DESCRIPTION, TICKET_AC
#
# Exit codes:
#   0 — ticket fetched and parsed successfully
#   1 — usage error, missing env vars, or Jira API failure

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 TICKET_KEY" >&2
    exit 1
fi

TICKET_KEY="$1"

if [ -z "${JIRA_BOT_EMAIL:-}" ] || [ -z "${JIRA_BOT_API_TOKEN:-}" ]; then
    echo "Error: JIRA_BOT_EMAIL and JIRA_BOT_API_TOKEN must be set" >&2
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════"
echo " worker-fetch-ticket  —  $TICKET_KEY"
echo "═══════════════════════════════════════════"
echo ""

curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
    "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY" \
    -o /tmp/worker_ticket_raw.json

python3 - "$TICKET_KEY" << 'PYEOF'
import sys
import json

ticket_key = sys.argv[1]

with open("/tmp/worker_ticket_raw.json") as f:
    data = json.load(f)

fields = data["fields"]
summary = fields.get("summary", "")

def extract_adf(node):
    """Recursively extract plain text from an ADF node."""
    if node is None:
        return ""
    node_type = node.get("type", "")
    if node_type == "text":
        return node.get("text", "")
    if node_type == "hardBreak":
        return "\n"
    if node_type == "paragraph":
        content = "".join(extract_adf(c) for c in node.get("content", []))
        return content + "\n"
    if node_type == "heading":
        level = node.get("attrs", {}).get("level", 2)
        content = "".join(extract_adf(c) for c in node.get("content", []))
        return "#" * level + " " + content + "\n"
    if node_type in ("bulletList", "orderedList"):
        items = []
        for i, child in enumerate(node.get("content", []), 1):
            prefix = "- " if node_type == "bulletList" else f"{i}. "
            items.append(prefix + extract_adf(child).strip())
        return "\n".join(items) + "\n"
    if node_type == "listItem":
        return "".join(extract_adf(c) for c in node.get("content", []))
    if node_type == "taskList":
        # Jira renders a markdown "- [ ]" checklist (e.g. Acceptance Criteria) as a
        # native taskList/taskItem pair, not bulletList/listItem — without this case
        # every taskItem falls through to the generic fallback below, which recurses
        # with no trailing newline, running all items together into one string and
        # erasing the line-start boundary the next heading's regex split depends on.
        items = []
        for child in node.get("content", []):
            items.append("- " + extract_adf(child).strip())
        return "\n".join(items) + "\n"
    if node_type == "taskItem":
        return "".join(extract_adf(c) for c in node.get("content", []))
    if node_type == "inlineCard":
        return node.get("attrs", {}).get("url", "")
    if node_type == "doc":
        return "".join(extract_adf(c) for c in node.get("content", []))
    # fallback: recurse into content
    return "".join(extract_adf(c) for c in node.get("content", []))

raw_desc = fields.get("description")
full_description = extract_adf(raw_desc).strip() if raw_desc else ""

# Split description into body and AC section.
# AC lives after "## Acceptance Criteria" or "**Acceptance Criteria**".
description_body = full_description
acceptance_criteria = ""

import re
ac_split = re.split(r"(?m)^#{1,3}\s+Acceptance Criteria", full_description)
if len(ac_split) >= 2:
    description_body = ac_split[0].strip()
    # Grab everything up to the next ## heading (Implementation Notes, etc.)
    remainder = ac_split[1]
    next_section = re.split(r"(?m)^#{1,3}\s+\w", remainder)
    acceptance_criteria = next_section[0].strip()
else:
    description_body = full_description

def shell_quote_value(value):
    """Wrap a multiline value for safe sourcing via printf into env file."""
    # Escape single quotes by ending the quote, adding an escaped single quote, then reopening.
    escaped = value.replace("'", "'\\''")
    return f"'{escaped}'"

with open("/tmp/worker_ticket.env", "w") as out:
    out.write(f"export TICKET_SUMMARY={shell_quote_value(summary)}\n")
    out.write(f"export TICKET_DESCRIPTION={shell_quote_value(description_body)}\n")
    out.write(f"export TICKET_AC={shell_quote_value(acceptance_criteria)}\n")

# Extract Relevant Files section
relevant_files_section = ""
rf_split = re.split(r"(?m)^#{1,3}\s+Relevant Files", full_description)
if len(rf_split) >= 2:
    rf_remainder = rf_split[1]
    next_section = re.split(r"(?m)^#{1,3}\s+\w", rf_remainder)
    relevant_files_section = next_section[0].strip()

relevant_file_paths = []
if relevant_files_section:
    for line in relevant_files_section.split("\n"):
        line = line.strip()
        if line.startswith("-"):
            path = line.lstrip("- ").split(" — ")[0].split(" - ")[0].strip().strip("`")
            if path:
                relevant_file_paths.append(path)

print(f"✅  Ticket fetched: {ticket_key}")
print(f"    Summary: {summary}")
print(f"    AC items: {acceptance_criteria.count(chr(10)) + 1 if acceptance_criteria else 0} lines")
if relevant_file_paths:
    print(f"    Relevant files:")
    for path in relevant_file_paths:
        print(f"      {path}")
else:
    print(f"    Relevant files: none parsed — see Relevant Files text below, if any")

# Print full ticket text directly — this is what step 1 of ticket-work.md relies on
# as the model's actual input. Env vars in /tmp/worker_ticket.env only live in the
# shell process that sources them; a later, separate Bash call cannot see them, so
# printing here (not just to the env file) is what makes this content available for
# the rest of the run without an extra echo/cat round-trip.
print("")
print("--- Context ---")
print(description_body)
print("")
print("--- Acceptance Criteria ---")
print(acceptance_criteria)
if relevant_files_section:
    print("")
    print("--- Relevant Files ---")
    print(relevant_files_section)
PYEOF

echo ""
echo "✅  Written to /tmp/worker_ticket.env"
