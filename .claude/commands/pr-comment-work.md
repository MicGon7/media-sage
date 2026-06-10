## Judgment vs mechanical audit (pattern: `docs/tool-consolidation-pattern.md`)

| Step | Type | Notes |
|---|---|---|
| Checkout branch | Mechanical | Single deterministic git command |
| Read PR diff + reviewer comment | Judgment | Sets up context for the answer |
| Compose and post PR comment | Judgment | Answer content requires understanding the question |
| Write /tmp/jira_comment.txt | Judgment | Summarizing what was answered |

**Shared comment/curl script assessment:** The `gh pr comment` call is a single command. Wrapping it in a script saves zero turns — the model still needs a turn to decide the comment body. No consolidation opportunity here.

**MS-357 rule:** `scripts/worker-*.sh` must never appear in a ticket's "Relevant files" section. They are pipeline tools to call, not implementation context to read.

---

1. Check out the branch specified in the prompt.
2. Read the PR diff and the reviewer's comment to understand what is being asked.
3. Read only the files referenced in the PR diff and the ticket's Relevant files section — do not explore broadly. The diff already scopes what matters; limit your investigation to those files and their immediate dependencies.
4. Answer the reviewer's question by posting a PR comment: `gh pr comment <pr-number> --body '🤖 **Agent:** your answer here'`
5. Do NOT push any code changes — this job type is for answering questions only.
6. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines.
