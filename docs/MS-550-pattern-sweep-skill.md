# MS-550: Pattern-Sweep Skill — Surface Recurring Gate Failures

## What Changed

Added an on-demand `/pattern-sweep` skill (`.claude/commands/pattern-sweep.md`). A human runs
it to find the same *class* of **gate failure** repeating across autonomous runs, and it
recommends how strongly to enforce a fix: a `CLAUDE.md` prose note, a test, or a `detekt` rule.
Its only output is a recommendation — it never opens a PR, dispatches a worker, or edits a rule.

This is a **docs/skill-only change** — no Kotlin, no orchestrator, no new infra. Nothing is
wired into a trigger; the skill is pull-based.

### Scope: type-1 only (gate failures)

The skill ships as the **gate-failure detective** and nothing more. Detecting recurring
`pr-quality-work` *review comments* (type-2 — reading merged-PR comment text via `gh` and
clustering it) is deliberately deferred to a follow-up. See **Why type-1 only** below.

### Before

The learning loop that once did this (per-job scorer + auto-PR feedback scanner) was retired in
MS-567 / MS-574 — `FeedbackPrService` was deleted because auto-opening PRs from cross-run
"learnings" was the wrong shape (unbounded, hard to review, drift-prone). But the *worthwhile*
half — noticing that the worker keeps failing the same gate for the same reason and graduating
it into a permanent check — was still valuable. It just had no home.

### After

```
human sees the Slack gate-failure trend line → runs /pattern-sweep
  → query_runs(status=FAILED); drops rows older than 7 days; groups by failed_gate
  → for each gate with ≥ 3 failures: reads analyze_run / explain_failure summaries (bounded)
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

### One data source, already exists — the advisor MCP

`query_runs` tells you *which* gate recurs (`failed_gate`); `analyze_run` / `explain_failure` give
the *specific* cause (e.g. every `detekt` failure is `MaxLineLength`). The advisor is a passive
tool provider — the cross-run reasoning happens in the session's own context, so **no new advisor
capability was added**.

### Summaries only — no raw-transcript path

The advisor's read-time slimming (`preprocessTranscript` — keeps head/tail + error/test/exit-status
"signal" lines, with a whole-document head/tail trim as a hard ceiling) is coupled *inside*
`analyze_run` and `explain_failure`. Both are **bounded regardless of run size** and return only a
summary to the session. The skill uses those two and **deliberately offers no raw
`fetch_transcript` path**: a raw transcript dumps the entire run into the session context — a
90-turn run is 200k–500k+ tokens, costly and able to overflow the turn. Earlier drafts tried to
make raw fetch safe with a `numTurns` guard and a run-count cap, but a guard living in prose that
the model is merely asked to honor is fragile — the durable fix is to not offer the dangerous
primitive at all. If both summaries leave the cause ambiguous, the skill reports it as unclear and
moves on rather than pulling the raw log. A scoped sweep is ~$1–2 with no blowup possible.

### 7-day window, ≤ 8 runs

`query_runs` has no date filter, so the skill pulls the recent 30 failed runs and drops rows older
than 7 days in-session — matching the Slack nudge (`≥ 3× in 7 days`). It inspects at most 8 runs
per sweep and names any overflow gates rather than sweeping unbounded.

### Why type-1 only (type-2 deferred)

Type-2 — scraping `pr-quality-work` review-comment text from merged PRs via `gh` and clustering it
by issue class — is the speculative half: more moving parts (GitHub pagination, comment parsing,
fuzzy clustering), less certain payoff, and it operates on *successful* runs where no gate flags
anything. Shipping type-1 first proves the value on the cheap, bounded path (advisor summaries over
a 7-day window) before investing in comment-scraping. If on-demand gate sweeps prove insufficient
and familiar review comments keep recurring, type-2 gets its own ticket. This keeps v1 small enough
that the skill needs almost no guards — there is no unbounded primitive left to fence off.

### Enforcement ladder: stronger only when statically checkable

The recommendation picks the *strongest* level the pattern can support — but a detekt rule is only
ever recommended for a mechanically-decidable violation. A judgment call (challenge whether a
convention is correct, verify reachability, idiom/altitude) gets a `CLAUDE.md` note, never a rule.
Recommending a static rule for a judgment call would be the exact "correct implementation of a
*wrong* rule" failure this pipeline already guards against. The skill is required to state which
recurrences are **not** statically checkable.

### Pull-based, no new trigger

The existing Slack gate-failure trend line (`JobCompletionNotifier` fires `⚠️ gate \`X\` failed in
N runs over the last M days` when a gate fails ≥ 3× in 7 days) is the nudge to run the skill — not
an automatic invocation. No new trigger was built.

### Sizing the graduation is the human's call

- **Small** — a `detekt.yml` toggle/threshold, or one `CLAUDE.md` line → the human just edits it.
  Firing an autonomous worker for a one-liner is disproportionate.
- **Substantive** — a custom detekt `Rule` class + test + registration → the human files a ticket
  and the normal `ticket-work` worker implements it. The worker is one option, not the default.

## Files Changed

| File | Change |
|---|---|
| `.claude/commands/pattern-sweep.md` | New skill — the gate-failure detective (7-day window, summary-only reads, enforcement ladder, advisory-only guardrails) |
| `docs/MS-550-pattern-sweep-skill.md` | This learning doc |

## Acceptance Criteria (type-1 scope)

- [x] A `/pattern-sweep` skill exists that, on demand, pulls recent failed runs and surfaces recurring gate failures.
- [x] It reads gate failures + run summaries via the advisor MCP tools (`query_runs`, `analyze_run`, `explain_failure`) — no new advisor capability, no raw-transcript dump.
- [x] For each recurrence it recommends an enforcement level: CLAUDE.md note, test, or detekt rule — and says which patterns are NOT statically checkable.
- [x] Output is advisory only. The skill never opens a PR or dispatches a worker itself.
- [x] Skill docs state the human decides implementation: direct edit for small changes, a ticket for substantive detekt rules.
- [ ] *(Deferred to follow-up)* Recurring `pr-quality-work` review-comment detection via `gh`.

## Explain Like I'm in 6th Grade

Imagine a robot helper that does your chores, and it keeps failing the *same test* the same way —
like it always mis-measures the flour and the cake flops. You could remind it every time (slow), or
put a note on the counter that says "level the cup," or buy a measuring cup that levels itself so
the mistake becomes *impossible*.

This tool, `/pattern-sweep`, is a detective that reads the robot's *report cards* (not its whole
diary — just the short summary of what went wrong), notices "it flopped the same way 4 times this
week," and tells *you*: here's the mistake, and here's the best way to stop it — a note, a test, or
the self-leveling cup. It never fixes anything itself. **You** decide. That's on purpose: an earlier
version fixed things on its own and made a mess, so now the human always makes the final call. We
also kept it small on purpose — it only reads the short summaries, so it can never accidentally haul
in a giant transcript and cost a fortune.

## Post-deploy verification

None required — this ships as a skill file consumed by the interactive session (and by the worker's
runtime `git clone`) once merged to `main`. To try it: run `/pattern-sweep` in a session with the
advisor MCP connected (`/mcp`) and confirm it lists recurring gate failures and recommendations
without opening a PR or dispatching a worker.
