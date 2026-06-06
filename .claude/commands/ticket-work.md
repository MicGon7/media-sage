1. Read the ticket description and acceptance criteria. If the ticket is not already In Progress in Jira, transition it now.
2. Create a feature branch: `git checkout -b feature/MS-{TICKET_KEY}-short-description`
3. Read the files listed in the ticket's "Relevant files" section before writing any code. If the task is already done, follow the graceful exit rule in CLAUDE.md Agent Guidelines.
4. Implement the changes described in the ticket.
5. Re-read the acceptance criteria. If any AC item requires unit tests, invoke `/unit-test` now (the branch is already checked out — skip branch creation inside that skill). If any AC item requires UI/composable tests, invoke `/ui-test` now (same — skip branch creation). Both may apply to the same ticket.
6. Run `./scripts/run-affected-tests.sh` — never run bare `./gradlew :module:test` directly.
7. Run `./gradlew detekt` and fix any violations.
8. Update Jira AC checkboxes as each criterion is met.
9. Write a learning doc under `docs/` if warranted — see the learning doc rule in CLAUDE.md Agent Guidelines.
10. Commit all changes with prefix `MS-{TICKET_KEY}: Description` and push: `git push -u origin <branch>`.
11. Open a PR: `gh pr create` — fill in title and body per the PR template at `.github/pull_request_template.md`.
12. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines.
13. Transition the Jira ticket to In Review.

