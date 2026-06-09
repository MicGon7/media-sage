1. Retrieve the ticket from Jira (cloudId: media-sage.atlassian.net) using the ticket key from the prompt. Extract the acceptance criteria.
2. Find the open PR for this ticket: `gh pr list --state open --search "head:feature/{TICKET_KEY}" --json number,url,headRefName --limit 1`. If no open PR is found, post a comment on the PR or log that no PR was found and exit.
3. Fetch the full PR diff: `gh pr diff <pr-number>`.
4. Evaluate the diff against each acceptance criteria item independently. Only verdict on items that are verifiable from the diff or codebase state — skip any AC items that are CI gates (e.g. "Detekt passes", "tests pass", "PR targets main"). For each diff-verifiable item, determine:
   - ✅ Met — the diff clearly satisfies the criterion. Include a one-line explanation of what in the diff satisfies it.
   - ❌ Not met — the diff does not address the criterion, or introduces a regression. You MUST include a one-line explanation of what is missing or wrong. "Not met" with no reason is not a valid verdict.
   - ⚠️ Partial — the criterion is partially addressed but something is missing. Include what is present and what is missing.
5. Post a PR review comment (NOT an approval or request-for-changes) with a structured verdict:
   ```
   gh pr review <pr-number> --comment --body "$(cat <<'EOF'
   🤖 **Agent:** Judge verdict for {TICKET_KEY}

   Evaluated PR diff against acceptance criteria from the original Jira ticket.

   **Verdict per AC item:**
   ✅ {ac item 1} — {one line explaining what in the diff satisfies it}
   ❌ {ac item 2} — {one line explaining what is missing or wrong}
   ⚠️ {ac item 3} — {one line explaining what is present and what is missing}

   **Overall:** {PASS if all ✅ / FAIL if any ❌ / PARTIAL if any ⚠️}

   This verdict is informational. The human reviewer makes the final call.
   EOF
   )"
   ```
6. Do NOT approve the PR, request changes, or merge. Post a comment only — the human reviewer acts on the verdict.
7. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines. Do NOT post via the Atlassian MCP — the orchestrator reads this file from the Pub/Sub completion event and posts it as Media Sage Bot.
