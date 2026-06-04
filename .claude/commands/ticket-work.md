1. Read the ticket description and acceptance criteria. If the ticket is not already In Progress in Jira, transition it now.
2. Create a feature branch: `git checkout -b feature/MS-{TICKET_KEY}-short-description`
3. Read the files listed in the ticket's "Relevant files" section before writing any code.
4. Implement the changes described in the ticket.
5. Run `./scripts/run-affected-tests.sh` — never run bare `./gradlew :module:test` directly.
6. Run `./gradlew detekt` and fix any violations.
7. Update Jira AC checkboxes as each criterion is met.
8. Write a learning doc under `docs/` following the existing doc format. Commit it alongside the code changes.
9. Commit all changes and push: `git push -u origin <branch>`.
10. Open a PR: `gh pr create` — fill in title and body per the PR template at `.github/pull_request_template.md`.
11. Write `/tmp/jira_comment.txt` using the exact format from the Agent Guidelines in CLAUDE.md (plain text, no bold markdown, pipeline checkpoints, PR URL, quality gate results, AC summary).
12. Transition the Jira ticket to In Review.

Follow the Agent Guidelines in CLAUDE.md for commit conventions, branch naming, and PR format.
