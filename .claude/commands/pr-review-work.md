## Judgment vs mechanical audit (pattern: `docs/tool-consolidation-pattern.md`)

| Step | Type | Notes |
|---|---|---|
| Checkout branch | Mechanical | Single deterministic git command |
| Read review comment + source files | Judgment | Requires understanding what the reviewer meant |
| Implement fix or compose explanation | Judgment | Code or prose from scratch |
| Run tests + detekt | Mechanical → `worker-quality.sh` | Fully deterministic pass/fail |
| Push fix commit | Mechanical | Single git push |
| Re-request review | Mechanical | Single gh CLI call |
| Write /tmp/jira_comment.txt | Judgment | Summarizing what changed |

See CLAUDE.md's Agent Guidelines Rules for cross-job rules that also apply here: trust your operational inputs (do not verify them) and no narration between steps.

**Inline PR comment posting assessment:** The `gh pr comment` for explanations is a single command that follows a judgment step (composing the explanation). Consolidating it into a script saves zero turns.

**MS-357 rule:** `scripts/worker-*.sh` must never appear in a ticket's "Relevant files" section.

---

1. Derive all PR context from `$PR_NUMBER`:
   ```bash
   gh pr view "$PR_NUMBER" --json headRefName,baseRefName,reviews,number
   ```
   From the response: check out `headRefName`, identify the most recent `changes_requested` review (reviewer login + body), and derive the ticket key from the branch name (`[A-Z]+-\d+` pattern).
2. Read the review comment carefully, then read the relevant source files to understand the context.
3. If a code change is needed: make the fix, then run quality gates:
   ```bash
   ./scripts/worker-quality.sh
   ```
   If it exits non-zero, follow the blocker stop rule — post a Jira comment and exit. Then push: `git add -p && git commit -m "MS-{TICKET_KEY}: Description" && git push --force-with-lease`.
4. If no code change is needed: post a PR comment explaining why using `gh pr comment <pr-number> --body '🤖 **Agent:** your explanation here'` and skip the push.
5. Re-request review from the original reviewer: `gh pr review-request <pr-number> --reviewer <login>`
6. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines. Do NOT post via the Jira REST API — the entrypoint appends metrics and posts it directly after you exit.
