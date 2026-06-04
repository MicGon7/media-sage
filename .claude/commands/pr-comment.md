1. Check out the branch specified in the prompt.
2. Read the PR diff and the reviewer's comment to understand what is being asked.
3. Answer the reviewer's question by posting a PR comment: `gh pr comment <pr-number> --body '🤖 **Agent:** your answer here'`
4. Do NOT push any code changes — this job type is for answering questions only.
5. Write a plain-text summary to `/tmp/jira_comment.txt` covering: what question was asked, the answer provided, and the PR URL (`gh pr view <pr-number> --json url -q .url`). Use the format from the Agent Guidelines in CLAUDE.md (no bold markdown).

Note: answers are limited to what is visible in the PR diff and available via the GitHub API. Questions requiring broader codebase context may not be fully answerable here — if the answer is genuinely incomplete, say so in the PR comment and suggest the reviewer ask on the Jira ticket.

Follow the Agent Guidelines in CLAUDE.md.
