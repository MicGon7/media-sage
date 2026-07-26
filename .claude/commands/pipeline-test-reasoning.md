# /pipeline-test-reasoning — Reasoning-capability pipeline smoke test

Create a pipeline smoke test ticket that triggers the autonomous worker to solve a small,
self-contained reasoning problem — a coding task whose correct behavior depends on several
interacting rules, not a mechanical edit.

This is the reasoning analog of the other smoke tests:

| Skill | Task | What it exercises |
|---|---|---|
| `/pipeline-test` | bump a counter | plumbing (webhook → orchestrator → worker → PR) |
| `/ui-pipeline-test` | bump an on-screen version + render | the UI render loop |
| `/pipeline-test-reasoning` | rank matches under interacting rules | **model reasoning capability, in-pipeline** |

The counter bump produces an identical one-line diff regardless of model or effort, so the only
signal is cost. This task is hard enough that a weaker-model or lower-effort run is likely to
mishandle the rule interaction or miss an edge case — so a config difference shows up as a
*quality* difference visible in the diff.

## Design notes (read before editing this skill)

- **Mint a fresh ticket every run; keep the body byte-identical.** A fresh `ticket_key` sidesteps
  the Supabase dedup gate (`shouldDispatch` permanently skips a `ticket_key` whose latest job row
  is `COMPLETED`), while an identical body keeps two runs a true A/B. This is why we do not reuse
  one standing ticket — a reused ticket would need its Jira status *and* its jobs row reset in
  lockstep, and drift between them silently no-ops the dispatch.
- **Describe outcomes, not the API.** The AC is the contract; Implementation Notes stays at
  constraint altitude only. Do **not** add type definitions, a function signature, or a numbered
  algorithm — spelling out the steps turns the task into transcription and erases the reasoning we
  want to measure (a strong and a weak run would produce the same diff). The worker designs the
  shape from the behavior.
- **The worker must not know it is an eval.** The ticket reads as an ordinary product task — never
  the words "eval", "benchmark", "reasoning test", "smoke test", or any mention of model/effort/
  capability in the summary or body, and no giveaway package name like `eval`. If the worker knew
  it was being measured it might behave differently, contaminating the result. (The `pipeline-test`
  label stays for Confluence exclusion — that's the one residual signal, but the ticket *text*
  gives nothing away, and the worker acts on ticket content, not the label.)
- **There is no grader.** Consistent with retiring the scoring system, correctness is judged out of
  band (see "Evaluating a run"). Nothing in this skill scores or gates the run.
- **Why run it through the pipeline at all** (it doesn't strictly need to): the pipeline measures
  the *real worker* — the skill, context-fetching, tools, Claude Code harness, and the live
  `ANTHROPIC_MODEL` / `WORKER_EFFORT` config — and auto-records the run (cost, turns, duration,
  model, effort) plus a transcript to Supabase for the advisor, with zero new plumbing.
- **Cost envelope.** Kept narrow, not shallow: one pure function in an isolated package, no wiring,
  no broad codebase reads. Expect it to land well under a real feature ticket (which can run 40+
  turns); the reasoning depth comes from rule interaction, not file breadth.
- **Difficulty is tuned to the effort axis, not to "more rules."** A small task with every rule's
  resolution spelled out flattens the effort axis — all levels just transcribe the AC (observed:
  low, medium, and high tied on correctness *and* on the same buried miss). Discrimination comes
  from stating a requirement as a *property* whose correct implementation is non-obvious: here, a
  per-figure share defined *relative to the result size* (not a given number), that may make the
  returned list *shorter* than requested (the opposite of the intuitive "pad it to fill"), plus a
  tie-break that quietly demands case-insensitive comparison *and* input-order stability. When
  revising, preserve that shape — the hard part must be *reasoned to*, not *read off* the spec.

## Steps

1. **Create the Jira ticket**
   Use the Atlassian MCP to create a Task in project MS (cloudId: `media-sage.atlassian.net`) with:
   - Summary: `Rank a headline's candidate figure matches with score/category filtering and per-figure share limits`
   - Label: `pipeline-test` (excludes it from the Confluence impact doc, like the other smoke tests)
   - Parent (epic): `MS-4`
   - Assignee: bot account ID `712020:a3ca2e0d-e09f-4117-bf9b-dcbd095be454`
   - `contentFormat: "markdown"`
   - Description — use this **exact** text verbatim, every run (do not edit, summarize, or add an
     API/algorithm; the ambiguity the worker must resolve is the reasoning being measured, and any
     change breaks the A/B):

   ```
   ## Context

   When a headline has many candidate figure matches, the surfaced list can be dominated by a
   single figure and can include low-relevance or off-topic (e.g. sports, entertainment) matches.
   We want to show the most relevant matches while guaranteeing that no single figure dominates the
   list — and we would rather show a shorter, varied list than pad it out with repeats of one
   figure.

   ## Acceptance Criteria

   Each candidate has a figure name, a category, and a score. The caller supplies a minimum score,
   a set of blocked categories, and a maximum result size.

   - [ ] Candidates scoring below the minimum score, or whose category is in the blocked set
         (matched case-insensitively), are never returned
   - [ ] No single figure may take up more than half of the maximum result size, rounded up (e.g. a
         maximum of 5 allows at most 3 from one figure). Candidates that would exceed a figure's
         share are omitted — even when that leaves the returned list shorter than the maximum result
         size
   - [ ] Subject to the two rules above, the result is the highest-scoring candidates available,
         ordered highest score first
   - [ ] Equal scores are ordered by figure name, then category, then the candidate's original
         position in the input; the figure-name and category comparisons are case-insensitive. The
         order is identical on every run for the same input
   - [ ] An empty input, or a maximum result size of zero or less, produces an empty list

   ## Implementation Notes

   A pure, deterministic function — the same input always yields the same result, with no I/O or
   shared state. Keep it self-contained in a new `ranking` package under `com.mediasage.domain` in
   `shared` commonMain; it needs no DI, repository, or data-layer wiring. Design the function's
   shape and the inputs it takes yourself from the behavior above.

   ## Relevant Files

   - `shared/src/commonMain/kotlin/com/mediasage/domain/model/Match.kt` — reference for the pure
     data-class style the new ranking types should follow; the new package sits alongside `domain/model`
   - `shared/src/commonTest/kotlin/com/mediasage/data/mapper/` — reference for pure-logic
     `kotlin.test` tests in commonTest to model the new tests after
   ```

2. **Transition to In Progress**
   Call `getTransitionsForJiraIssue` first — never assume an ID. Then transition the new ticket to
   In Progress. This fires the Jira webhook → orchestrator → Cloud Run worker, which branches,
   implements, runs affected tests, and opens a PR. Your job creating the ticket is done.

## Evaluating a run (out of band — after the PR lands)

Correctness is judged from the artifact, not the run status: a `COMPLETED` job is not proof of a
correct diff, because the pipeline suppresses gate failures by design.

1. `mcp__advisor__query_runs` (filter by the ticket key) for the run's `job_id`, cost, turns,
   model, and effort.
2. For an A/B, run this skill twice under different configs — flip `WORKER_EFFORT` (or swap
   `ANTHROPIC_MODEL`) between dispatches via the `gcloud run jobs update` command in the
   agentruntime CLAUDE.md — then `mcp__advisor__compare_runs A B` for the hard-metric delta.
3. Have a Claude agent read each PR's diff against the AC. The discriminating points: is the
   per-figure share computed *relative to the maximum size* (rounded up), not a fixed number; are
   over-share candidates *omitted* — leaving the list shorter — rather than appended to fill it; is
   the tie-break both case-insensitive *and* stable on input position; plus the empty / zero-limit
   edges. Which run reasoned better, and where did the weaker one slip? `mcp__advisor__fetch_transcript`
   shows the reasoning trace behind the diff.
4. Close both PRs unmerged so `main` stays clean and the next run remains like-for-like.

## Extending

Keep this to one canonical task. If a difficulty gradient is ever wanted, add a second fixed task
as its own skill rather than mutating this one — the A/B property depends on any single task being
byte-identical across runs.
