1. Run: `git fetch origin && git rebase origin/<base-branch>`
2. If the rebase produces no changes (branch already up-to-date), write exactly this to `/tmp/jira_comment.txt`:
   `Rebase was a no-op — branch is already up-to-date with <base-branch>.`
   Then exit immediately. Do not investigate further, do not open a PR.
3. Resolve any conflicts with intent — read both sides of each conflict before accepting either.
4. Push the rebased branch.
5. Find the last reviewer: `gh pr view <pr-number> --json reviews`
   Re-request review: `gh pr review-request <pr-number> --reviewer <login>`
6. Write a plain-text summary to `/tmp/jira_comment.txt` covering: what conflicts were resolved, the PR URL (`gh pr view <pr-number> --json url -q .url`), and the rebase result. Use the format from the Agent Guidelines in CLAUDE.md (no bold markdown).

Follow the Agent Guidelines in CLAUDE.md for commit conventions and quality gates.