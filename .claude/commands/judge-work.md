## Judgment vs mechanical audit (pattern: `docs/tool-consolidation-pattern.md`)

| Step | Type | Notes |
|---|---|---|
| curl Jira + extract AC | Mechanical | Deterministic fetch |
| Evaluate diff against AC | Judgment | Requires reading and interpreting code |
| Post PR review comment | Mechanical | Single gh CLI call after judgment |
| Write /tmp/jira_comment.txt | Judgment | Summarizing the verdict |

**Shared comment/curl script assessment:** The PR review comment is a single `gh pr review` call that always follows the eval step. Wrapping it in a script saves zero turns — the model still needs a turn to compose the verdict body. No consolidation opportunity.

**MS-357 rule:** `scripts/worker-*.sh` must never appear in a ticket's "Relevant files" section.

**Never emit a text-only turn.** Each turn must contain at least one tool call. Do not announce what you are about to do — proceed directly to action.

---

1. Read `$PR_NUMBER` and `$TICKET_KEY` from env. If `$PR_NUMBER` is unset or empty, fall back to: `gh pr list --state open --search "head:feature/$TICKET_KEY" --json number,url,headRefName --limit 1`.
2. In a single parallel turn, issue both of the following tool calls:
   - **Fetch Jira AC:** `curl -s -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY"`
   - **Fetch PR diff:** `gh pr diff <pr-number>`
3. Evaluate the diff against each acceptance criteria item independently. Only verdict on items that are verifiable from the diff or codebase state — skip any AC items that are CI gates (e.g. "Detekt passes", "tests pass", "PR targets main"). For each diff-verifiable item, determine:
   - ✅ Met — the diff clearly satisfies the criterion. Include a one-line explanation of what in the diff satisfies it.
   - ❌ Not met — the diff does not address the criterion, or introduces a regression. You MUST include a one-line explanation of what is missing or wrong. "Not met" with no reason is not a valid verdict.
   - ⚠️ Partial — the criterion is partially addressed but something is missing. Include what is present and what is missing.
4. In a single turn, issue both of the following tool calls in parallel — they have no dependency on each other:

   **a) Post a PR review comment** (NOT an approval or request-for-changes) with a structured verdict:
   ```
   gh pr review <pr-number> --comment --body "$(cat <<'EOF'
   🤖 **Agent:** Judge verdict for $TICKET_KEY

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

   **b) Write `/tmp/jira_comment.txt`** — see the Jira comment file rule in CLAUDE.md Agent Guidelines. Do NOT post via curl or any Jira API — the entrypoint appends metrics and posts directly after you exit.

5. Do NOT approve the PR, request changes, or merge. Post a comment only — the human reviewer acts on the verdict.
