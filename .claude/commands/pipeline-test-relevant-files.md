# /pipeline-test-relevant-files — Relevant Files section ablation (multi-file work)

Mint a fresh, disguised Jira Task describing a small **multi-file** feature (a new file plus edits to
two existing files the worker must discover), dispatch it, and measure how the ticket's
**Relevant Files** section affects the run. The task shape gives the section real files to point at
and real cross-file discovery to save — or, when stale, to misdirect.

## Rules that keep the A/B valid

- **Body byte-identical every run; only the `## Relevant Files` arm changes.** That section is the
  sole variable. Everything from `## Context` through `## Implementation Notes` is verbatim each run.
- **Same model + effort across arms.** Flipping `WORKER_EFFORT` / `ANTHROPIC_MODEL` confounds it.
- **Fresh `ticket_key` every run** to clear the Supabase dedup gate (`shouldDispatch` skips a
  `ticket_key` whose latest job row is `COMPLETED`). Never reuse one standing ticket.
- **No algorithm, no file layout in the body.** Spelling out the steps or the call sites hands the
  worker the discovery the section is supposed to supply — the AC states behavior only.
- **Disguised, no grader.** No "eval / benchmark / ablation / smoke test / relevant files" or
  model/effort wording, and no giveaway package name. The `pipeline-test` label (Confluence
  exclusion) is the only residual signal; the worker acts on ticket text. Correctness is judged out
  of band (see "Evaluating a run").

## The fixed task body (verbatim every run — `## Context` through `## Implementation Notes`)

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

Append **exactly one** as a trailing `## Relevant Files` section; that block is the only difference
between runs.

**Arm A — present and correct:**

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

**Arm B — removed:** append nothing; the body ends at `## Implementation Notes`. The worker must find
`EntityMappers.kt` and `QuoteRepositoryImpl.kt` on its own.

**Arm C — stale/wrong:** arm A's block with the same prose but four paths swapped for plausible-but-
wrong ones, so following the section causes failed Reads and a wrong-file edit (the MS-577 case):

- `EntityMappers.kt` → `data/mapper/ThemeMappers.kt` (does not exist)
- `QuoteRepositoryImpl.kt` → `data/repository/FigureRepositoryImpl.kt` (real, but handles no themes)
- `domain/model/Quote.kt` → `domain/model/Theme.kt` (does not exist)
- `commonTest/.../mapper/EntityMappersTest.kt` → `commonTest/.../repository/QuoteRepositoryImplTest.kt`

Run C **only if arm A beats arm B** — if a correct section saves nothing, its staleness is moot.
When A > B, C vs B measures whether a section that will inevitably drift is still net-positive.

## Steps

1. **Create the Jira ticket** via the Atlassian MCP — a Task in project MS (cloudId:
   `media-sage.atlassian.net`) with:
   - Summary: `Normalize and de-duplicate figure and quote theme tags across storage`
   - Label: `pipeline-test`
   - Parent (epic): `MS-4`
   - Assignee: bot account ID `712020:a3ca2e0d-e09f-4117-bf9b-dcbd095be454`
   - `contentFormat: "markdown"`
   - Description: the fixed body plus the Relevant Files block for the arm you are running.

2. **Transition to In Progress** — call `getTransitionsForJiraIssue` first (never assume an ID), then
   transition. This fires the webhook → orchestrator → Cloud Run worker, which branches, implements,
   tests, and opens a PR.

## Evaluating a run (out of band, after the PR lands)

A `COMPLETED` job is not proof of a correct diff — the pipeline suppresses gate failures by design.
Run arms A and B at the same model + effort; add C only if A beats B. Then:

1. **Hard metrics.** `mcp__advisor__query_runs` (by ticket key) for `job_id`, cost, turns, model,
   effort; `mcp__advisor__compare_runs A B` (and A vs C) for the deltas.
2. **Discovery vs process turns.** `mcp__advisor__analyze_run` on each run; separate turns spent
   *finding* the call sites (Glob/Grep/failed Reads) from turns spent implementing. Arm A should
   spend near-zero discovery; B more; C wasted discovery (failed Reads, then re-discovery). If A and
   B are indistinguishable, the section earned nothing here.
3. **Quality drift.** Have an agent read each PR diff against the AC: did the run find **both** call
   sites, or leave an ad-hoc join behind? Is the de-dup case-insensitive, first-occurrence-casing,
   order-preserving, round-trip-stable? Did C edit the wrong file or invent a non-existent one?
   `mcp__advisor__fetch_transcript` shows the trace behind the diff.
4. **Close all PRs unmerged** so `main` stays like-for-like for the next run.

## Extending

Keep to one canonical task — the A/B/C property depends on the body staying byte-identical. For a
different shape (e.g. a UI seam), add a new skill rather than mutating this one.
