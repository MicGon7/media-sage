1. Check out the branch specified in the prompt.
2. Read the review comment carefully, then read the relevant source files to understand the context.
3. If a code change is needed: make the fix, run `./scripts/run-affected-tests.sh`, run `./gradlew detekt`, then push a fix commit with prefix `MS-{TICKET_KEY}: Description`.
4. If no code change is needed: post a PR comment explaining why using `gh pr comment <pr-number> --body '🤖 **Agent:** your explanation here'` and skip the push.
5. Re-request review from the original reviewer: `gh pr review-request <pr-number> --reviewer <login>`
6. Write `/tmp/jira_comment.txt` using the exact format defined in CLAUDE.md Agent Guidelines (Jira comment file rule). Content: what was changed (or why no change was needed), PR URL (`gh pr view <pr-number> --json url -q .url`), quality gate results.

