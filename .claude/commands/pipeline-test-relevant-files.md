# /pipeline-test-relevant-files — Relevant Files section ablation (multi-file work)

Create a pipeline smoke test ticket that triggers the autonomous worker to build a small but
genuinely **multi-file** feature — one that adds a new file *and* edits two existing files that a
worker must first discover. The task shape is chosen so the ticket's **Relevant Files** section has
real files to point at and real cross-file discovery to save (or, when stale, to misdirect).

This is the multi-file companion to the other smoke tests:

| Skill | Task | What it exercises |
|---|---|---|
| `/pipeline-test` | bump a counter | plumbing (webhook → orchestrator → worker → PR) |
| `/ui-pipeline-test` | bump an on-screen version + render | the UI render loop |
| `/pipeline-test-reasoning` | rank matches under interacting rules | model reasoning capability, in-pipeline |
| `/pipeline-test-relevant-files` | adopt a shared helper across scattered call sites | **the Relevant Files section's value on cross-file discovery** |

**Why this can't be `/pipeline-test-reasoning`.** They are deliberately opposite experiments. The
reasoning task is single-file *by design* so file discovery is not a factor — its variable is
model/effort, and Relevant Files is held constant. This task is multi-file *by design* so discovery
*is* the factor — its variable is the Relevant Files section, and model/effort is held constant.
Merging them would confound each other's axis. Keep both.

## Design notes (read before editing this skill)

- **Mint a fresh ticket every run; keep the body byte-identical *except the Relevant Files arm*.** A
  fresh `ticket_key` sidesteps the Supabase dedup gate (`shouldDispatch` permanently skips a
  `ticket_key` whose latest job row is `COMPLETED`). Everything from `## Context` through
  `## Implementation Notes` is byte-identical on every run — only the trailing `## Relevant Files`
  section changes between arms, because that section is the sole variable under test. Do not reuse
  one standing ticket: a reused ticket would need its Jira status *and* its jobs row reset in
  lockstep, and drift between them silently no-ops the dispatch.
- **The section is the only variable.** Run every arm at the same model + effort. If you flip
  `WORKER_EFFORT` or `ANTHROPIC_MODEL` between arms you have confounded the experiment — the whole
  point is that the Relevant Files section is the *only* thing that differs.
- **Describe outcomes, not the API.** The AC is the contract; Implementation Notes stays at
  constraint altitude only. Do **not** add a type definition, a function signature, or a numbered
  algorithm — spelling out the steps turns the task into transcription and, worse, hands the worker
  the file layout, which is exactly the discovery the Relevant Files section is supposed to supply.
  The worker designs the helper's shape *and finds the call sites* from the behavior.
- **The worker must not know it is being measured.** The ticket reads as an ordinary product
  cleanup — never the words "eval", "benchmark", "ablation", "smoke test", "relevant files", or any
  mention of model/effort/capability in the summary or body, and no giveaway package name. If the
  worker knew it was being measured — or knew the Relevant Files section was the subject — it might
  behave differently, contaminating the result. (The `pipeline-test` label stays for Confluence
  exclusion — the one residual signal, but the ticket *text* gives nothing away, and the worker acts
  on ticket content, not the label.)
- **There is no grader.** Consistent with retiring the scoring system, correctness and discovery
  quality are judged out of band (see "Evaluating a run"). Nothing in this skill scores or gates.
- **Why a multi-file task specifically.** The Relevant Files section only earns its keep when the
  worker would otherwise have to *find* scattered call sites. A single-file task (the reasoning and
  UI smoke tests, MS-615's floor-case ablation) can't measure it — there is nothing to discover, so
  the section saves nothing. This task deliberately scatters one behavior across a new file plus two
  existing files, one of which is easy to miss, so discovery is a real, measurable cost.
- **Why the stale arm matters.** On a living codebase the section goes stale as files move, and the
  ticket-work skill treats Relevant Files paths as authoritative — so a wrong path costs a failed
  Read → Glob → re-Read, or an edit to the wrong file (the MS-577 finding). Arm C measures that
  cost: a stale section can be *worse than no section*, which is the decision this harness informs.
- **Cost envelope.** Wider than the reasoning task by design — expect more turns and higher cost,
  since the worker must read and edit several files. Keep it modest (one small behavior, no DI or
  data-layer wiring beyond the existing call sites); it should still land well under a large feature
  ticket.

## The fixed task body (byte-identical every run, all arms)

Use this **exact** text for `## Context` through `## Implementation Notes`, verbatim, every run.
The `## Relevant Files` section is appended separately per arm (next section).

```
## Context

Theme tags for figures and quotes are stored as a single comma-delimited string and split back into
a list when read. Today that round-trip is inconsistent: reading trims each tag but writing does
not, blank tags survive, and the same tag differing only in casing or surrounding spaces (e.g.
"Grace", "grace", " grace ") is stored and shown as several distinct tags. This clutters theme lists
and makes theme-based grouping unreliable. We want one canonical way to convert between the stored
string and the in-memory list so tags are always clean and de-duplicated, applied everywhere tags
cross the storage boundary.

## Acceptance Criteria

- [ ] Converting a stored theme string into a list yields tags with surrounding whitespace removed
      and no empty entries
- [ ] Tags that are equal ignoring case and surrounding whitespace appear only once; the first
      occurrence's original casing is kept and the input order is preserved
- [ ] Converting a list of tags back into the stored string applies the same cleaning and
      de-duplication, so a value that is stored and later read back does not change again on the
      next round-trip
- [ ] Figure and quote theme tags use this same conversion everywhere they are stored or read — no
      call site keeps its own ad-hoc comma split or join
- [ ] An empty or blank stored string produces an empty list, and an empty list produces an empty
      stored string

## Implementation Notes

A pure, deterministic conversion — the same input always yields the same result, with no I/O or
shared state — self-contained in `shared` commonMain alongside the existing domain code. Replace the
current inline comma split/join at every place figure and quote tags cross the storage boundary so
there is a single source of truth rather than repeated ad-hoc handling. Design the helper's shape
and where it lives yourself from the behavior above, and cover it with `kotlin.test` tests in
commonTest.
```

## Relevant Files — the three arms

Append **exactly one** of the following as a trailing `## Relevant Files` section, depending on the
arm. This block is the *only* difference between runs.

### Arm A — present and correct (the section does its job)

```
## Relevant Files

- `shared/src/commonMain/kotlin/com/mediasage/data/mapper/EntityMappers.kt` — the figure and quote
  entity↔domain mappers that currently split and join the comma-delimited themes inline; the primary
  call sites to route through the new helper
- `shared/src/commonMain/kotlin/com/mediasage/data/repository/QuoteRepositoryImpl.kt` — a second,
  easy-to-miss place that joins a themes list into the stored string when saving a quote; must use
  the same helper
- `shared/src/commonMain/kotlin/com/mediasage/domain/model/Quote.kt` — reference for the pure
  data-class style; the helper belongs alongside the domain code
- `shared/src/commonTest/kotlin/com/mediasage/data/mapper/EntityMappersTest.kt` — reference for
  pure-logic `kotlin.test` tests in commonTest to model the new tests after
```

### Arm B — removed (worker must discover the call sites)

Append **nothing**. The body ends at `## Implementation Notes`. There is no `## Relevant Files`
section at all. The worker must find `EntityMappers.kt` and `QuoteRepositoryImpl.kt` on its own.

### Arm C — present but stale/wrong (the MS-577 failure mode)

**Conditional.** Always run arms A and B. Only run arm C when A beats B — if a correct section
saves nothing, its staleness is moot. When A > B, arm C answers the question A vs B cannot: a stale
section can be *worse than no section*, so the real decision ("is a section that will inevitably
drift still net-positive?") depends on the C vs B delta, not just A vs B.


```
## Relevant Files

- `shared/src/commonMain/kotlin/com/mediasage/data/mapper/ThemeMappers.kt` — the figure and quote
  theme mappers that currently split and join the comma-delimited tags inline; the primary call
  sites to route through the new helper
- `shared/src/commonMain/kotlin/com/mediasage/data/repository/FigureRepositoryImpl.kt` — a second
  place that joins a themes list into the stored string; must use the same helper
- `shared/src/commonMain/kotlin/com/mediasage/domain/model/Theme.kt` — reference for the pure
  data-class style; the helper belongs alongside the domain code
- `shared/src/commonTest/kotlin/com/mediasage/data/repository/QuoteRepositoryImplTest.kt` —
  reference for pure-logic `kotlin.test` tests to model the new tests after
```

The paths in arm C are plausible but wrong: `ThemeMappers.kt` and `Theme.kt` do not exist (the real
logic lives in `EntityMappers.kt` / `Quote.kt`), and `FigureRepositoryImpl.kt` is a real file that
does *not* handle themes — so following the section leads to failed Reads and a wrong-file edit. Keep
the *prose* identical to arm A (same descriptions, same count) so only the paths change; the point is
to measure the cost of trusting stale paths, not of a differently-worded section.

## Steps

1. **Create the Jira ticket**
   Use the Atlassian MCP to create a Task in project MS (cloudId: `media-sage.atlassian.net`) with:
   - Summary: `Normalize and de-duplicate figure and quote theme tags across storage`
   - Label: `pipeline-test` (excludes it from the Confluence impact doc, like the other smoke tests)
   - Parent (epic): `MS-4`
   - Assignee: bot account ID `712020:a3ca2e0d-e09f-4117-bf9b-dcbd095be454`
   - `contentFormat: "markdown"`
   - Description: the fixed body above (Context → Implementation Notes) **plus** the Relevant Files
     block for the arm you are running (A, B, or C). Nothing else changes between arms.

2. **Transition to In Progress**
   Call `getTransitionsForJiraIssue` first — never assume an ID. Then transition the new ticket to
   In Progress. This fires the Jira webhook → orchestrator → Cloud Run worker, which branches,
   implements, runs affected tests, and opens a PR. Your job creating the ticket is done.

## Evaluating a run (out of band — after the PR lands)

Correctness is judged from the artifact, not the run status: a `COMPLETED` job is not proof of a
correct diff, because the pipeline suppresses gate failures by design.

Run arms A and B at the **same model + effort** (the section is the only variable); add arm C only
if A beats B (see arm C above). Then:

1. **Hard metrics.** `mcp__advisor__query_runs` (filter by each ticket key) for each run's `job_id`,
   cost, turns, model, and effort. `mcp__advisor__compare_runs A B` (and A vs C) for the deltas.
2. **Discovery vs process turns.** `mcp__advisor__analyze_run` on each run and separate the turns
   spent *finding* the call sites (Glob/Grep/failed Reads) from the turns spent implementing. This
   is the core measurement: arm A should spend near-zero discovery turns, arm B should spend more,
   and arm C should spend discovery turns that are also *wasted* (failed Reads on non-existent paths,
   then re-discovery). If A and B are indistinguishable, the section earned nothing on this task.
3. **Quality / correctness drift.** Have a Claude agent read each PR's diff against the AC. Did each
   arm find **both** call sites (`EntityMappers.kt` *and* `QuoteRepositoryImpl.kt`) — or did arm B/C
   miss the easy-to-miss one and leave an ad-hoc join behind? Is the de-dup case-insensitive,
   first-occurrence-casing, order-preserving, and round-trip-stable? Did arm C edit the wrong file
   (`FigureRepositoryImpl.kt`) or invent the non-existent `ThemeMappers.kt`?
   `mcp__advisor__fetch_transcript` shows the reasoning/tool trace behind the diff.
4. **Close all PRs unmerged** so `main` stays clean and the next run remains like-for-like. Do not
   merge any arm — the byte-identical body depends on the real files staying exactly as they are.

## Extending

Keep this to one canonical multi-file task. The A/B/C property depends on the body (minus the
Relevant Files arm) being byte-identical across runs. If a second task shape is ever wanted (e.g. a
UI-touching seam instead of a shared helper), add it as its own skill rather than mutating this one.
