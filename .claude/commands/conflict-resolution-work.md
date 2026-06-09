1. Run: `git fetch origin && git rebase origin/<base-branch>`
2. If the rebase produces no changes (branch already up-to-date), write exactly this to `/tmp/jira_comment.txt`:
   `Rebase was a no-op — branch is already up-to-date with <base-branch>.`
   Then exit immediately. Do not investigate further, do not open a PR.
3. Resolve any conflicts with intent — read both sides of each conflict before accepting either.
4. Push the rebased branch.
5. Find the last reviewer: `gh pr view <pr-number> --json reviews`
   Re-request review: `gh pr review-request <pr-number> --reviewer <login>`
6. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines. Do NOT post via the Jira REST API — the entrypoint appends metrics and posts it directly after you exit.

