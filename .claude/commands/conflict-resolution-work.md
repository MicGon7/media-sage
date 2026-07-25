## Judgment vs mechanical audit (pattern: `docs/tool-consolidation-pattern.md`)

| Step | Type | Notes |
|---|---|---|
| git fetch + rebase | Judgment | Conflict resolution requires reading both sides |
| Push rebased branch | Mechanical | Single deterministic command |
| Find last reviewer + re-request | Mechanical | Deterministic gh CLI calls |
| Write /tmp/jira_comment.txt | Judgment | Summarizing what was resolved |

**`worker-quality.sh` / `worker-ship.sh` assessment:** These scripts do not apply here. `worker-quality.sh` runs tests and detekt against new code — conflict resolution doesn't introduce new code to test. `worker-ship.sh` opens a PR and transitions a ticket — this job type has an existing PR and no Jira transition needed. The two mechanical steps (push + re-request review) are a single sequential shell operation and consolidating them into a script would save at most one turn; not worth the indirection.

---

1. Derive branch and base branch from `$PR_NUMBER`:
   ```bash
   gh pr view "$PR_NUMBER" --json headRefName,baseRefName
   ```
   Check out `headRefName`, then run: `git fetch origin && git rebase origin/<baseRefName>`
2. If the rebase produces no changes (branch already up-to-date), write exactly this to `/tmp/jira_comment.txt`:
   `Rebase was a no-op — branch is already up-to-date with <base-branch>.`
   Then exit immediately. Do not investigate further, do not open a PR.
3. Resolve any conflicts with intent — read both sides of each conflict before accepting either.
4. Push the rebased branch.
5. Find the last reviewer: `gh pr view <pr-number> --json reviews`
   Re-request review: `gh pr review-request <pr-number> --reviewer <login>`
6. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines. Do NOT post via the Jira REST API — the entrypoint appends metrics and posts it directly after you exit.
