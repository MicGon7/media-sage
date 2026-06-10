## Judgment vs mechanical audit (pattern: `docs/tool-consolidation-pattern.md`)

| Step | Type | Notes |
|---|---|---|
| curl Jira + extract AC | Mechanical | Deterministic fetch |
| Evaluate diff against AC | Judgment | Requires reading and interpreting code |
| Post PR review comment | Mechanical | Single gh CLI call after judgment |
| Fetch + parse worker run log | Mechanical | Inline curl — no script dependency |
| Write /tmp/jira_comment.txt | Judgment | Summarizing the verdict |

**Shared comment/curl script assessment:** The PR review comment is a single `gh pr review` call that always follows the eval step. Wrapping it in a script saves zero turns — the model still needs a turn to compose the verdict body. No consolidation opportunity.

**MS-357 rule:** `scripts/worker-*.sh` must never appear in a ticket's "Relevant files" section.

**Never emit a text-only turn.** Each turn must contain at least one tool call. Do not announce what you are about to do — proceed directly to action.

---

1. Retrieve the ticket from Jira via curl. Extract the acceptance criteria.
   ```bash
   curl -s -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
     "https://media-sage.atlassian.net/rest/api/3/issue/$JIRA_TICKET_KEY"
   ```
2. Find the open PR for this ticket: `gh pr list --state open --search "head:feature/{TICKET_KEY}" --json number,url,headRefName --limit 1`. If no open PR is found, post a comment on the PR or log that no PR was found and exit.
3. Fetch the full PR diff: `gh pr diff <pr-number>`.
4. Evaluate the diff against each acceptance criteria item independently. Only verdict on items that are verifiable from the diff or codebase state — skip any AC items that are CI gates (e.g. "Detekt passes", "tests pass", "PR targets main"). For each diff-verifiable item, determine:
   - ✅ Met — the diff clearly satisfies the criterion. Include a one-line explanation of what in the diff satisfies it.
   - ❌ Not met — the diff does not address the criterion, or introduces a regression. You MUST include a one-line explanation of what is missing or wrong. "Not met" with no reason is not a valid verdict.
   - ⚠️ Partial — the criterion is partially addressed but something is missing. Include what is present and what is missing.
5. Fetch the worker run log and extract metrics in one call:
   ```bash
   content_url=$(curl -sf \
     -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
     "https://media-sage.atlassian.net/rest/api/3/issue/$JIRA_TICKET_KEY?fields=attachment" \
     | python3 -c "
   import json, sys
   data = json.load(sys.stdin)
   for a in data.get('fields', {}).get('attachment', []):
       if a['filename'].startswith('worker-run-') and a['filename'].endswith('.jsonl'):
           print(a['content'])
           break
   " 2>/dev/null) && \
   [ -n "$content_url" ] && \
   curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" "$content_url" -o /tmp/worker-run.jsonl
   ```
   Then run `/cloud-job-breakdown` to get the metrics line (`N turns | $cost | Xm Ys`).
   If the attachment is not found, omit the turn efficiency section silently.
   Do NOT enumerate individual tool-use turns from the JSONL — that approach is fragile and
   fails on thinking blocks. Write the wasted-turns narrative from contextual judgment instead:
   look at the turn count relative to ticket complexity, and note patterns visible in the diff
   (e.g. a trivial one-line change that took 12 turns signals a script failure or recovery loop).
6. In a single turn, issue both of the following tool calls in parallel — they have no dependency on each other:

   **a) Post a PR review comment** (NOT an approval or request-for-changes) with a structured verdict:
   ```
   gh pr review <pr-number> --comment --body "$(cat <<'EOF'
   🤖 **Agent:** Judge verdict for {TICKET_KEY}

   Evaluated PR diff against acceptance criteria from the original Jira ticket.

   **Verdict per AC item:**
   ✅ {ac item 1} — {one line explaining what in the diff satisfies it}
   ❌ {ac item 2} — {one line explaining what is missing or wrong}
   ⚠️ {ac item 3} — {one line explaining what is present and what is missing}

   **Overall:** {PASS if all ✅ / FAIL if any ❌ / PARTIAL if any ⚠️}

   ---

   **Turn efficiency** (from worker run log):
   {total} turns | ${cost} | {duration}

   Wasted turns:
   - Turn N: {description} — {reason it was wasted}

   Recommendation: {one-line action the human or pipeline author can take}

   This verdict is informational. The human reviewer makes the final call.
   EOF
   )"
   ```
   Omit the "Turn efficiency" section entirely if the worker run log was not found.

   **b) Write `/tmp/jira_comment.txt`** — see the Jira comment file rule in CLAUDE.md Agent Guidelines. Do NOT post via curl or any Jira API — the entrypoint appends metrics and posts directly after you exit.

7. Do NOT approve the PR, request changes, or merge. Post a comment only — the human reviewer acts on the verdict.
