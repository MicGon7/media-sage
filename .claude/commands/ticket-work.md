1. The ticket is already In Progress — do not call jira_get_issue or transition it again. Read the ticket description and acceptance criteria from the prompt.
2. Create a feature branch: `git checkout -b feature/MS-{TICKET_KEY}-short-description`
3. Read the files listed in the ticket's "Relevant files" section before writing any code. If the task is already done, follow the graceful exit rule in CLAUDE.md Agent Guidelines.
4. Implement the changes described in the ticket.
5. Re-read the acceptance criteria. If any AC item requires unit tests, invoke `/unit-test` now (the branch is already checked out — skip branch creation inside that skill). If any AC item requires UI/composable tests, invoke `/ui-test` now (same — skip branch creation). Both may apply to the same ticket.
6. Run `./scripts/run-affected-tests.sh` — never run bare `./gradlew :module:test` directly.
7. Run `./gradlew detekt` and fix any violations.
8. Update Jira AC checkboxes as each criterion is met.
9. Write a learning doc under `docs/` if warranted — see the learning doc rule in CLAUDE.md Agent Guidelines.
10. Commit all changes with prefix `MS-{TICKET_KEY}: Description` and push: `git push --force-with-lease -u origin <branch>`.
11. Open a PR with `gh pr create`. Use this body structure (do not read the PR template file — use this directly):
    ```
    ## Summary
    <!-- 1-3 bullet points describing what this PR does -->

    ## Ticket
    <!-- Link to Jira ticket, e.g. MS-XX -->

    ## Type of Change
    - [ ] New feature
    - [ ] Bug fix
    - [ ] Refactor
    - [ ] Tests
    - [ ] CI/CD
    - [ ] Documentation

    ## Testing
    - [ ] Unit tests added/updated
    - [ ] Integration tests added/updated
    - [ ] Manual testing performed

    ## Author
    - [x] Agent-authored (reviewed by human)

    ## Checklist
    - [ ] Tests pass locally (`./gradlew allTests`)
    - [ ] No API keys or secrets in code
    - [ ] CLAUDE.md updated (if new pattern introduced)
    ```
12. Write `/tmp/jira_comment.txt` — see the Jira comment file rule in CLAUDE.md Agent Guidelines.
13. Transition the Jira ticket to In Review.

