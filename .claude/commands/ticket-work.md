1. Read the ticket description and acceptance criteria. If the ticket is not already In Progress in Jira, transition it now.
2. Create a feature branch: `git checkout -b feature/MS-{TICKET_KEY}-short-description`
3. Read the files listed in the ticket's "Relevant files" section before writing any code.
4. Implement the changes described in the ticket.
5. Run `./scripts/run-affected-tests.sh` — never run bare `./gradlew :module:test` directly.
6. Run `./gradlew detekt` and fix any violations.
7. Update Jira AC checkboxes as each criterion is met.
8. Write a learning doc under `docs/` following the existing doc format. Commit it alongside the code changes.
9. Commit all changes with prefix `MS-{TICKET_KEY}: Description` and push: `git push -u origin <branch>`.
10. Open a PR: `gh pr create` — fill in title and body per the PR template at `.github/pull_request_template.md`.
11. Write `/tmp/jira_comment.txt` — see Agent Guidelines for format rules. Content must include:
    - Task description
    - Pipeline checkpoints (webhook → orchestrator → worker → PR → Pub/Sub pending)
    - PR URL
    - Quality gate results (detekt, affected tests)
    - Diff summary
    - Acceptance criteria checklist
    - Do not include a "Run metrics" section — the orchestrator appends that after you exit.

12. Transition the Jira ticket to In Review.

