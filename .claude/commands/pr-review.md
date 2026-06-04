1. Check out the branch specified in the prompt.
2. Read the review comment carefully, then read the relevant source files to understand the context.
3. If a code change is needed: make the fix, run `./scripts/run-affected-tests.sh`, run `./gradlew detekt`, then push a fix commit with prefix `MS-{TICKET_KEY}: Description`.
4. If no code change is needed: post a PR comment explaining why using `gh pr comment <pr-number> --body '🤖 **Agent:** your explanation here'` and skip the push.
5. Re-request review from the original reviewer: `gh pr review-request <pr-number> --reviewer <login>`
6. Write `/tmp/jira_comment.txt` in plain text (no bold markdown) covering: what was done, the PR URL (`gh pr view <pr-number> --json url -q .url`), and quality gate results. Use the pipeline checkpoints format from the ticket-work skill, adapted for a PR fix (omit Jira transition checkpoint).

Follow the Agent Guidelines in CLAUDE.md for standing rules (no pushing to main, no secrets, stop-and-comment-if-blocked, OOM rule).
