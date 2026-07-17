# MS-550: Pattern-Sweep Skill — Surface Recurring Worker Mistakes

## What Changed

Added an on-demand `/pattern-sweep` skill (`.claude/commands/pattern-sweep.md`). A human runs
it to find the same *class* of mistake repeating across autonomous runs, and it recommends how
strongly to enforce each one: a `CLAUDE.md` prose note, a test, or a `detekt` rule. Its only
output is a recommendation — it never opens a PR, dispatches a worker, or edits a rule.

This is a **docs/skill-only change** — no Kotlin, no orchestrator, no new infra. Nothing is
wired into a trigger; the skill is pull-based.

### Before

The learning loop that once did this (per-job scorer + auto-PR feedback scanner) was retired in
MS-567 / MS-574 — `FeedbackPrService` was deleted because auto-opening PRs from cross-run
"learnings" was the wrong shape (unbounded, hard to review, drift-prone). But the *worthwhile*
half — noticing that the worker keeps making the same mistake and graduating it into a permanent
check — was still valuable. It just had no home.

### After

```
human sees a recurrence (or the Slack gate-failure trend line) → runs /pattern-sweep
  → reads recent FAILED runs + transcripts via advisor MCP (type-1: gate failures)
  → reads recent pr-quality-work review comments live from GitHub via gh (type-2: review comments)
  → clusters by shared cause; anything seen ≥ 3× is a recurrence
  → per recurrence: recommends detekt rule | test | CLAUDE.md note, and says which are prose-only
  → STOPS. Human decides whether/how to graduate it.
```

## Key Decisions

### Advisory-only, human-in-the-loop — the deliberate replacement for the auto-PR path

The whole reason the old feedback scanner was retired is that it *acted* on its findings. This
skill inverts that: it is a **detective**, the human is the **decider**, and `ticket-work` is the
**implementer** only when the fix is big enough to justify a job. The skill's guardrails say, in
so many words, never open a PR / dispatch a worker / edit a rule. The doc explicitly warns against
resurrecting `FeedbackPrService`.

### Two recurrence types, two data sources — both already exist

- **Type-1 (gate-failure recurrence)** uses the **advisor MCP** (`query_runs`, `fetch_transcript`,
  `analyze_run`). `query_runs` tells you *which* gate recurs (`failed_gate`); the transcripts tell
  you the *specific* cause (e.g. every `detekt` failure is `MaxLineLength`). The advisor is a
  passive tool provider — the cross-run reasoning happens in the session's own context, so **no new
  advisor capability was added**.
- **Type-2 (review-comment recurrence)** uses `gh` to read `pr-quality-work` review bodies live
  from GitHub (state `COMMENT`, prefixed `🤖 **Agent:**`). These runs *succeed*, so no gate flags
  them — only reading across recent quality-review PRs surfaces them. GitHub is the source of truth;
  we deliberately **do not** copy the comment text into Supabase (that duplicates GitHub and adds a
  drift-prone write path — the completion event already stores `reviewCommentCount`, just not text).

### Enforcement ladder: stronger only when statically checkable

The recommendation picks the *strongest* level the pattern can support — but a detekt rule is only
ever recommended for a mechanically-decidable violation. A judgment call (challenge whether a
convention is correct, verify reachability, idiom/altitude) gets a `CLAUDE.md` note, never a rule.
Recommending a static rule for a judgment call would be the exact "correct implementation of a
*wrong* rule" failure this pipeline already guards against. The skill is required to state which
recurrences are **not** statically checkable.

### Pull-based, no new trigger

Type-1 is *nudged* by the existing Slack gate-failure trend line (`JobCompletionNotifier` fires
`⚠️ gate \`X\` failed in N runs over the last M days` when a gate fails ≥ 3× in 7 days) — but that
line is a nudge to run the skill, not an automatic invocation. Type-2 has no nudge and needs none:
the human is already in the PR. We explicitly did **not** build a type-2 push trigger — on-demand
first; add automation only if sweeps prove insufficient.

### Sizing the graduation is the human's call

- **Small** — a `detekt.yml` toggle/threshold, or one `CLAUDE.md` line → the human just edits it.
  Firing an autonomous worker for a one-liner is disproportionate.
- **Substantive** — a custom detekt `Rule` class + test + registration → the human files a ticket
  and the normal `ticket-work` worker implements it. The worker is one option, not the default.

## Files Changed

| File | Change |
|---|---|
| `.claude/commands/pattern-sweep.md` | New skill — the detective's instructions (both recurrence types, enforcement ladder, advisory-only guardrails) |
| `docs/MS-550-pattern-sweep-skill.md` | This learning doc |

## Acceptance Criteria

- [x] A `/pattern-sweep` skill exists that, on demand, pulls recent runs and surfaces recurring worker mistakes.
- [x] It reads gate failures + transcripts via the advisor MCP tools (no new advisor server capability added).
- [x] It reads `pr-quality-work` review-comment text live from GitHub via `gh` (no Supabase copy of comments).
- [x] For each recurrence it recommends an enforcement level: CLAUDE.md note, test, or detekt rule — and says which patterns are NOT statically checkable.
- [x] Output is advisory only. The skill never opens a PR or dispatches a worker itself.
- [x] Skill docs state the human decides implementation: direct edit for small changes, a ticket for substantive detekt rules.

## Explain Like I'm in 6th Grade

Imagine a robot helper that does your chores. Sometimes it makes the *same little mistake* over
and over — like it always forgets to put the cap back on the toothpaste. You could remind it every
single time (annoying and slow), or you could put a sign on the mirror that says "CAP ON," or you
could buy a toothpaste tube that snaps shut by itself so the mistake becomes *impossible*.

This new tool, `/pattern-sweep`, is like a detective that reads the robot's diary and notices "hey,
it forgot the cap 5 times this week." Then it tells *you*: here's the mistake, here's how many times
it happened, and here's the best way to stop it — a sign, a test, or the self-closing tube. But it
never fixes anything on its own. **You** decide what to do. That's on purpose: an earlier version
used to fix things by itself and made a mess, so now the human always makes the final call.

## Post-deploy verification

None required — this ships as a skill file consumed by the interactive session (and by the worker's
runtime `git clone`) once merged to `main`. To try it: run `/pattern-sweep` in a session with the
advisor MCP connected (`/mcp`) and confirm it lists recurrences and recommendations without opening
a PR or dispatching a worker.
