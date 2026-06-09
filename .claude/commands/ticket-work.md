## Job-specific rules

**Jira comment file:** Write a plain-text summary to `/tmp/jira_comment.txt` before exiting. Do NOT post via the Jira REST API — the entrypoint appends metrics and posts it directly after you exit. Use this exact format (plain text only — no `**bold**` or other markdown):

```
🤖 Agent: Run summary for {TICKET_KEY}

Task: {one-line task description}

Pipeline checkpoints:
✅ Jira webhook fired when ticket moved to In Progress
✅ Orchestrator dispatched Cloud Run Job
✅ Worker cloned from michael-gonzalez-dev/media-sage successfully
✅ Worker completed the task and opened a PR
⏳ Pub/Sub completion event — fires after this comment
⏳ Job marked COMPLETED in Supabase — pending Pub/Sub

PR: {pr_url}

Quality gates:
✅ Detekt: {result}
✅ Affected tests: {result}

Diff: {summary}

Acceptance criteria:
✅ {ac_item}
```

Do not include a "Run metrics" section — the entrypoint appends metrics after you exit.

**Graceful exit when task is already done:** If the task is already fully satisfied by the current state of the code, do not invent work. Check off the relevant AC items, write `/tmp/jira_comment.txt` stating the task was already complete and what was found, transition the ticket to In Review, and exit.

**Learning doc:** Default to no learning doc. Write one only if the work meets at least one of:
- Introduces a new pattern not previously used in the codebase
- Makes an architectural decision with non-obvious tradeoffs
- Integrates a new external system or API

If the work follows an established pattern, makes a trivial change, or could have been completed just by reading existing code — skip the doc. When in doubt, skip. The burden of proof is on writing, not skipping.

**pipeline-test tasks:** Must be additive — choose work that provably does not exist yet. Never choose a task that may already be satisfied in the codebase.

---

1. The ticket is already In Progress — do not call jira_get_issue or transition it again. Read the ticket description and acceptance criteria from the prompt.
2. Create a feature branch: `git checkout -b feature/MS-{TICKET_KEY}-short-description`
3. Read the files listed in the ticket's "Relevant files" section before writing any code. If the task is already done, follow the graceful exit rule in CLAUDE.md Agent Guidelines.
4. Implement the changes described in the ticket.
5. Re-read the acceptance criteria. If any AC item requires unit tests, invoke `/unit-test` now (the branch is already checked out — skip branch creation inside that skill). If any AC item requires UI/composable tests, invoke `/ui-test` now (same — skip branch creation). Both may apply to the same ticket.
6. Run `./scripts/run-affected-tests.sh` — never run bare `./gradlew :module:test` directly.
7. Run `./gradlew detekt` and fix any violations.
8. Update Jira AC checkboxes as each criterion is met. Use curl to update the issue description with the checked boxes:
   ```bash
   # Get current issue (to read the existing description ADF)
   curl -s -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
     "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY"
   # Update description with checkboxes checked (PUT to same endpoint with updated ADF body)
   curl -s -X PUT -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"fields":{"description":<updated-adf-body>}}' \
     "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY"
   ```
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
13. Transition the Jira ticket to In Review via curl:
    ```bash
    # Get available transitions
    curl -s -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
      "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY/transitions"
    # Transition to In Review (use the transition ID from the response above)
    curl -s -X POST -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"transition":{"id":"<in-review-transition-id>"}}' \
      "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY/transitions"
    ```

