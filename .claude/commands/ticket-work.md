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
curl -sf -u "$JIRA_BOT_EMAIL:$JIRA_BOT_API_TOKEN" \
  "https://media-sage.atlassian.net/rest/api/3/issue/$TICKET_KEY/transitions" \
  -o /tmp/jira_transitions.json
TRANSITION_ID=$(python3 -c "
import json
with open('/tmp/jira_transitions.json') as f:
    ts = json.load(f)['transitions']
print(next(t['id'] for t in ts if t['name']=='In Review'))
")
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

**Worker must not explore `scripts/`:** Never use `find` or `ls` to discover worker scripts. Call them directly by their known paths (`./scripts/worker-init.sh`, `./scripts/worker-quality.sh`, `./scripts/worker-ship.sh`). They don't move.

**`worker-ship.sh` is terminal:** Once `worker-ship.sh` exits successfully, the job is done. Do not run git status, re-read the PR URL, re-check Jira, or run any command that duplicates what the script already covers.

**pipeline-test tasks:** Must be additive — choose work that provably does not exist yet. Never choose a task that may already be satisfied in the codebase.

**Never force push.** Always use `--force-with-lease`. If it is rejected, stop immediately — post a Jira comment describing the conflict and exit. Do not retry with bare `--force`.

**No ticket references in code.** Never put `MS-NNN` ticket numbers in inline comments, KDoc, or any source file. Ticket context belongs in commit messages and PR descriptions, not in the codebase — it rots the moment the ticket is closed or the code moves. Scan your diff before committing: if any added line contains `MS-\d+` inside a comment, remove it.

**Do not narrate between steps.** Never emit a text response at any point in a run — not between tool calls, not after `worker-ship.sh` succeeds. `worker-ship.sh` already prints a clean result block; do not add a text response after it. Every text response is a billable API round-trip. The only allowed narration is `echo` statements inside bash commands. If a step fails or requires a decision, a text response is appropriate; otherwise, proceed directly to the next tool call.

**No TodoWrite.** Do not call TodoWrite at any point during a worker run. There is no human watching the session UI in a Cloud Run Job — the task list is invisible and adds no value. The steps in this skill define the workflow; a parallel task list is redundant and wastes turns.

**Trust your operational inputs — do not verify them.** This is a non-interactive Cloud Run Job, not an interactive session. Treat operational inputs the way a shell script treats its arguments: use them directly, never inspect them first. Env vars and worker-script outputs are valid when the job starts. Concretely:
- After sourcing a worker script output (`worker-fetch-ticket.sh`, `worker-init.sh`), proceed immediately to the next step. Do not `cat` the env file, `echo` a var, or run any command whose only purpose is to confirm the previous step worked. The exit code is the signal — if the script succeeded, the vars are set.
- Never add a verification step between two productive steps. If a command fails, the non-zero exit code stops the job. That is the only signal you need.

**Tickets are goal-driven — you discover the files, they are not handed to you.** A ticket carries a Context (the goal's *why*) and Acceptance Criteria (the goal's verifiable done-state). It does **not** carry an authoritative file list. Locating the code a change touches is *your* job — done in the one batched Phase 1 discovery turn below, reasoning from the goal and the package layout documented in `CLAUDE.md`. If a ticket *does* still carry a `Relevant Files` or `Implementation Notes` section, treat it as a **non-authoritative hint**, never as truth: it may be stale, incomplete, or point at the wrong place. Confirm each hinted path actually exists and is genuinely involved during your discovery reads before relying on it — the same load-bearing-claim discipline below — and never let a hint's presence excuse you from discovery. Do not assume such a section is present; most tickets will not have one.

**Verify load-bearing claims in the ticket body — but only those.** The rule above governs *operational inputs* (env vars, worker-script outputs, whether a prior script succeeded) — use them directly, never inspect them. It does **not** cover *technical assertions in the ticket body*: a claim that a specific field, DTO, endpoint, function, or behaviour exists in the code is a hypothesis about code state, not an input. When the implementation depends on such a claim, confirm it during the Phase 1 discovery reads you already perform — you are opening those files anyway, so this costs no extra turns. Scope this strictly to load-bearing claims — the specific symbols the change is built on — not every sentence in the ticket; do not turn discovery into a skeptical audit.

When a load-bearing claim proves false, never silently paper over it — but do not reflexively stop either. Weigh the correction:
- **Small and in-scope** (a mis-named symbol, a field on a sibling type) → adapt, proceed, and note the correction in the PR body.
- **Crosses a module or API-contract boundary, changes the ticket's scope, or needs a product decision** → do not improvise a workaround. Surface it: if you have already written substantive code, open the PR — a **draft** if it is incomplete — with the open question and your recommended options at the top of the body and the Jira comment; if you have not yet written code, write `/tmp/jira_comment.txt` describing the gap and stop. Either way the human reviews the *decision*, never a silent divergence.

MS-87 is the failure this prevents: the run improvised a name-lookup workaround around a `figureId` field that did not exist on the DTO the note named, and shipped it as if the ticket's premise held.

**Trust your own writes — never verify a file you just wrote.** After a successful `Write` or `Edit`, proceed directly to the next productive step. Do not `Read` the file back, do not `git diff` it, do not `cat` it. `Write`/`Edit` return an error on failure — a silent success is a real success, there is no corruption to catch. This mirrors root `CLAUDE.md` "Trust your own writes"; the MS-87 run wasted turns re-reading and diffing files immediately after editing them. The only time to re-read a file after editing is when a *later, unrelated* step legitimately needs its current contents — never as a confirmation that the edit landed.

**No filler turns — every text response is a billable round-trip.** Beyond the "do not narrate between steps" rule below, do not emit a standalone text response that only summarizes progress, restates what a tool just printed, or announces what you are about to do next. Chain straight into the next tool call. In particular, when `run-affected-tests.sh` prints a skip notice (no Android/iOS SDK, or nothing affected), that notice is complete — do **not** `Read` or `cat` the script source to understand the skip, and do not narrate it. A skip is a non-failure; continue the run.

---

1. The ticket is already In Progress — do not transition it again. Fetch the ticket and set up the branch in one call:
   ```bash
   ./scripts/worker-fetch-ticket.sh "$TICKET_KEY" && source /tmp/worker_ticket.env && ./scripts/worker-init.sh "$TICKET_KEY" && source /tmp/worker_init.env
   ```
   The fetch script prints the ticket summary and the Context/AC body — that goal is your input to Phase 1 discovery in step 2. The init script derives the branch slug from `$TICKET_SUMMARY` automatically, and precomputes the render-test coverage list (`$WORKER_RENDER_COVERED` — the composables that already have a `captureRoboImage` block). Use that list in steps 2 and 5 instead of grepping for render blocks yourself.
   - `WORKER_BRANCH_STATUS=existing` → diff the existing PR (`gh pr diff "$WORKER_PR_URL"`), check each AC item against it. If all AC items are satisfied, follow the graceful exit rule and stop. If AC is incomplete, the branch is already checked out — continue from step 3.
   - `WORKER_BRANCH_STATUS=new` → branch is ready, proceed to step 2.
2. **Phase 1 – Discovery (one batched turn).** The ticket gives you the goal (Context) and the done-state (AC), not a file list — so discovery here means *locating* the code the change touches and reading it, all in one batched turn. Assemble the complete candidate set first, then fire every `Read` in a single message with one tool call per file. Do **not** read one file, look at it, and decide the next — that reactive one-at-a-time chain is the exact waste this phase exists to prevent. Do not start implementing until the batch returns. Any file you will edit must be in this batch — including docs like `CLAUDE.md`, module `CLAUDE.md` files, and skill files — so that no later `Edit` hits the "file has not been read yet" guard.

   **Build the candidate set from the goal — discover it yourself.** Infer where the code lives from the package layout documented in `CLAUDE.md`, then confirm by locating it — up front, in as few search round-trips as possible, before you read. Run the locating searches you need together, not one-then-decide-the-next: `grep` for the symbol, feature name, or string the change hinges on; `Glob` for a file whose exact path you cannot infer. **Never delegate file location to a sub-agent (Explore, Task, or any other) — do all discovery directly in this main loop.** A sub-agent runs in a separate context: you must still `Read` every file you edit here (the Edit guard requires it), so a sub-agent only re-reads files you then read again — duplicated cost with no saving. Direct `grep`/`Glob` + a batched `Read` locates any well-scoped ticket's files in this documented codebase, up to the largest feature-sized ones. Beyond the file the change obviously targets, pull in the co-located files a change silently fans out to — these are the easy-to-miss edit targets, not derivable from a single path:
   - ViewModel change → its Contract and Screen
   - DAO change → the entity, the repository implementation, and every Fake in `commonTest` that implements the DAO interface
   - New UI component → the nearest existing composable and its test file
   - Rename/refactor of a symbol → `grep` the symbol once, then every referencing file — source, tests, and docs (root and module `CLAUDE.md`, skills). Renames fan out into docs and tests; do not discover those reactively after the rename.
   - CLAUDE.md or skill-file change → both `CLAUDE.md` and the target skill file in full
   - A ticket that asserts a specific field/DTO/endpoint/function exists → locate and read that source file and confirm the claim before building on it (see "Verify load-bearing claims" above). This is the cheap moment to catch a false premise: you are reading the file anyway.
   - UI change to an *existing* screen → do **not** grep for a `captureRoboImage` block. `worker-init.sh` already printed `$WORKER_RENDER_COVERED` (the composables that have a render block); check the affected screen against that list. Step 5 reads the same list to decide whether to author a new block. Do not open the render test to read it end-to-end.

   Once the candidate set is resolved, fire all the `Read` calls in a single parallel batch. If your discovery turn ends with a single `Read`, or a `Read` followed by more reads in later turns to decide what to read next, you have failed this step. If the task turns out to be already done, follow the graceful exit rule in CLAUDE.md Agent Guidelines.

   When you reach Phase 2 for a rename, apply one `replace_all` `Edit` per file covering the import and every usage site at once — never a separate import-only pass followed by a body pass.
3. Implement the changes described in the ticket.
4. Re-read the acceptance criteria. If any AC item requires unit tests, invoke `/unit-test` now (the branch is already checked out — skip branch creation inside that skill). If any AC item requires UI/composable tests, invoke `/ui-test` now (same — skip branch creation). Both may apply to the same ticket.
5. **If the change touches any `composeApp` composable, render it and self-critique before shipping.** You cannot see the UI otherwise, so this is the only proofing step before a human pulls the branch.
   - **Ensure a render block exists for each affected screen, then build once.** Using the `$WORKER_RENDER_COVERED` list `worker-init.sh` printed (the composables that already have a `captureRoboImage` block), split the affected screens into two cases — do **not** grep or re-inspect the test files here:
     - *No existing render block* (a new screen/component) → author one `captureRoboImage("build/outputs/roborazzi/<name>.png") { MediaSageTheme { <Screen>(sampleState) } }` block now, in an `androidUnitTest` render test modelled on `SmokeTestScreenRenderTest`, **before** running the script. Prefer representative state that exposes layout risk (long text, missing image, populated lists), not empty/loading state.
     - *Already covered by a render block* (e.g. a tweak to a screen a `*RenderTest` already renders — like a version bump to `SmokeTestScreen`) → change nothing; the existing block re-renders with your edit when the script runs. Touch that test only if your edit changed the block's constructor call or sample state. Do **not** `Glob` for the test file and `Read` it end-to-end just to "confirm no change is needed" — that is a wasted ~2-turn detour; `$WORKER_RENDER_COVERED` already told you the block exists.
   - One block per screen: a ticket touching a main screen and a detail screen gets two. Author **all** new blocks in this single step, before the first `capture-ui.sh` run, so the run performs exactly one build.
   - Only after the render test exists, run `./scripts/capture-ui.sh` **exactly once, in the foreground** — a single blocking `Bash` call. Do not launch it as a background task and then poll `TaskOutput`; the script prints its `## UI screenshots` block and exits on its own, so a background run only adds redundant poll turns (the MS-87 run wasted a turn on a non-blocking poll immediately followed by a blocking one). If it is slow, raise the `Bash` timeout rather than backgrounding it. Never run it before the `captureRoboImage` block is written: `capture-ui.sh` triggers a full Gradle render build (~30s), and running it against a missing test both wastes that build and exits 1 ("render produced no PNGs"), forcing a second build after you write the test. One render test authored up front = one build per run. It renders every block, stages the PNGs, and prints a `## UI screenshots` Markdown block. If it exits 3 with a "no Android SDK" notice, the render is *skipped, not failed* — continue the run without screenshots.
   - `Read` each PNG under `docs/ui-screenshots/` and critique it: padding, alignment, spacing, truncation, theme colours, missing-image fallback. If anything is off, fix the composable and re-run the script. Do not ship UI you have not looked at.
   - Paste the printed `## UI screenshots` block into the PR body.

   Skip this entire step for non-UI changes.
6. Write a learning doc under `docs/` if warranted — see the learning doc rule in CLAUDE.md Agent Guidelines.
7. Write the PR body and ship in one call:
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
   ./scripts/worker-ship.sh "$TICKET_KEY" "MS-{TICKET_KEY}: Description"
   ```
   `worker-ship.sh` runs quality gates first (exits non-zero on failure), then commits, pushes, opens the PR, writes `/tmp/jira_comment.txt`, updates Jira AC checkboxes, and transitions the ticket to In Review.

