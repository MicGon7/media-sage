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
11. Write `/tmp/jira_comment.txt` in plain text (no bold markdown). Use this exact format:

    ```
    🤖 Agent: Run metrics summary for {TICKET_KEY}

    Task: {one-line task description}

    Pipeline checkpoints verified:
    ✅ Jira webhook fired when ticket moved to In Progress
    ✅ Orchestrator dispatched Cloud Run Job
    ✅ Worker cloned from michael-gonzalez-dev/media-sage successfully
    ✅ Worker completed the task and opened a PR
    ⏳ Pub/Sub completion event — fires after this comment
    ⏳ Job marked COMPLETED in Supabase — pending Pub/Sub
    ✅ Run metrics comment posted (this comment)

    PR: {pr_url}

    Quality gates:
    ✅ Detekt: {result}
    ✅ Affected tests: {result}

    Diff: {summary}

    Acceptance criteria:
    ✅ {ac_item}
    ```

    Do not include a "Run metrics" section — the orchestrator appends that after you exit.
    Do NOT post a Jira comment via the Atlassian MCP — the orchestrator reads this file and posts it.

12. Transition the Jira ticket to In Review.

Follow the Agent Guidelines in CLAUDE.md for standing rules (no pushing to main, no secrets, stop-and-comment-if-blocked, OOM rule).
