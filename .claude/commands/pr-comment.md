1. Check out the branch specified in the prompt.
2. Read the PR diff and the reviewer's comment to understand what is being asked.
3. Read only the files referenced in the PR diff and the ticket's Relevant files section — do not explore broadly. The diff already scopes what matters; limit your investigation to those files and their immediate dependencies.
4. Answer the reviewer's question by posting a PR comment: `gh pr comment <pr-number> --body '🤖 **Agent:** your answer here'`
5. Do NOT push any code changes — this job type is for answering questions only.
6. Write `/tmp/jira_comment.txt` — see Agent Guidelines for format rules. Content: the question asked, the answer provided, PR URL (`gh pr view <pr-number> --json url -q .url`).

