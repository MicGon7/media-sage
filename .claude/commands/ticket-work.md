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

PR: {pr_url}

Quality gates:
✅ Detekt: {result}
✅ Affected tests: {result}

Diff: {summary}

Acceptance criteria:
✅ {ac_item}
```

Do not include a "Run metrics" section — the entrypoint appends metrics after you exit.

**Graceful exit when task is already done:** If the task is already fully satisfied by the current state of the code, do not invent work. Check off the relevant AC items, write `/tmp/jira_comment.txt` stating the task was already complete and what was found, transition the ticket to In Review using bot credentials, and exit. Transition call:
```bash
TRANSITION_ID=$(curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
  "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY/transitions" \
  | python3 -c "import sys,json; ts=json.load(sys.stdin)['transitions']; print(next(t['id'] for t in ts if t['name']=='In Review'))")
curl -sf -X POST -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"transition\":{\"id\":\"$TRANSITION_ID\"}}" \
  "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY/transitions"
```

**Learning doc:** Default to no learning doc. Write one only if the work meets at least one of:
- Introduces a new pattern not previously used in the codebase
- Makes an architectural decision with non-obvious tradeoffs
- Integrates a new external system or API

If the work follows an established pattern, makes a trivial change, or could have been completed just by reading existing code — skip the doc. When in doubt, skip. The burden of proof is on writing, not skipping.

**Worker scripts are never "Relevant files":** `scripts/worker-*.sh` must never appear in a ticket's "Relevant files" section. They are pipeline tools to call, not implementation context to read.

**Worker must not explore `scripts/`:** Never use `find` or `ls` to discover worker scripts. Call them directly by their known paths (`./scripts/worker-init.sh`, `./scripts/worker-quality.sh`, `./scripts/worker-ship.sh`). They don't move.

**`worker-ship.sh` is terminal:** Once `worker-ship.sh` exits successfully, the job is done. Do not run git status, re-read the PR URL, re-check Jira, or run any command that duplicates what the script already covers.

**pipeline-test tasks:** Must be additive — choose work that provably does not exist yet. Never choose a task that may already be satisfied in the codebase.

**Never force push.** Always use `--force-with-lease`. If it is rejected, stop immediately — post a Jira comment describing the conflict and exit. Do not retry with bare `--force`.

**Do not narrate between steps.** Never emit a text response between tool calls — not to announce what you are about to do, not to summarise what just happened. The only allowed narration is `echo` statements inside bash commands. If a step fails or requires a decision, a text response is appropriate; otherwise, proceed directly to the next tool call.

---

1. The ticket is already In Progress — do not transition it again. Fetch the ticket description and acceptance criteria from Jira:
   ```bash
   curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
     "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY"
   ```
2. Run branch setup:
   ```bash
   ./scripts/worker-init.sh "$TICKET_KEY" "short-description" && source /tmp/worker_init.env
   ```
   - `WORKER_BRANCH_STATUS=existing` → diff the existing PR (`gh pr diff "$WORKER_PR_URL"`), check each AC item against it. If all AC items are satisfied, follow the graceful exit rule and stop. If AC is incomplete, the branch is already checked out — continue from step 4.
   - `WORKER_BRANCH_STATUS=new` → branch is ready, proceed to step 3.
3. Read the files listed in the ticket's "Relevant files" section before writing any code. If the task is already done, follow the graceful exit rule in CLAUDE.md Agent Guidelines.
4. Implement the changes described in the ticket.
5. Re-read the acceptance criteria. If any AC item requires unit tests, invoke `/unit-test` now (the branch is already checked out — skip branch creation inside that skill). If any AC item requires UI/composable tests, invoke `/ui-test` now (same — skip branch creation). Both may apply to the same ticket.
6. Run quality gates:
   ```bash
   ./scripts/worker-quality.sh
   ```
   The script runs tests and detekt in parallel, checks pre-existing violations automatically, and prints a clean pass/fail summary. If it exits non-zero, follow the blocker stop rule — post a Jira comment and exit.
7. Write a learning doc under `docs/` if warranted — see the learning doc rule in CLAUDE.md Agent Guidelines.
8. Write `/tmp/pr_body.md` and `/tmp/jira_comment.txt`:
   ```bash
   cat > /tmp/pr_body.md << 'PRBODY'
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
   PRBODY
   ```
   Write `/tmp/jira_comment.txt` per the Jira comment file rule in CLAUDE.md Agent Guidelines. Leave `{pr_url}` as a literal placeholder — `worker-ship.sh` prints the real URL to `/tmp/worker_pr_url.txt` after the PR is opened.
9. Ship everything in one call:
   ```bash
   ./scripts/worker-ship.sh "$TICKET_KEY" "MS-{TICKET_KEY}: Description"
   ```
   This commits, pushes, opens the PR (using `/tmp/pr_body.md`), updates Jira AC checkboxes, and transitions the ticket to In Review. The PR URL is printed and written to `/tmp/worker_pr_url.txt`.

