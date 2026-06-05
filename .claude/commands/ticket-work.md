1. Read the ticket description and acceptance criteria. If the ticket is not already In Progress in Jira, transition it now.
2. Create a feature branch: `git checkout -b feature/MS-{TICKET_KEY}-short-description`
3. Read the files listed in the ticket's "Relevant files" section before writing any code. If the task is already done, follow the graceful exit rule in CLAUDE.md Agent Guidelines.
4. Implement the changes described in the ticket.
5. Run `./scripts/run-affected-tests.sh` — never run bare `./gradlew :module:test` directly.
6. Run `./gradlew detekt` and fix any violations.
7. Update Jira AC checkboxes as each criterion is met.
8. Write a learning doc under `docs/` if warranted — see the learning doc rule in CLAUDE.md Agent Guidelines.
9. Commit all changes with prefix `MS-{TICKET_KEY}: Description` and push: `git push -u origin <branch>`.
10. Open a PR: `gh pr create` — fill in title and body per the PR template at `.github/pull_request_template.md`.
11. Write `/tmp/jira_comment.txt` using the exact format defined in CLAUDE.md Agent Guidelines (Jira comment file rule). Include task description, pipeline checkpoints, PR URL, quality gate results, diff summary, and AC checklist.
12. Transition the Jira ticket to In Review.

