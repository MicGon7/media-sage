# /pattern-sweep — Surface recurring gate failures and recommend how to enforce them

Reads recent **failed** pipeline runs, finds the same *class* of gate failure repeating across
runs, and — for each recurrence — recommends **how strongly to enforce it**: a `CLAUDE.md`
prose note (weakest), a test, or a `detekt` rule (strongest, statically-checkable patterns
only).

This is the surviving, advisory-only half of the retired learning loop (MS-567). It is a
**detective, not an implementer**: it never opens a PR, never dispatches a worker, and never
edits a rule. The human is the decider — and, for small changes, the implementer.

> ⚠️ Do **not** resurrect the auto-PR path. `FeedbackPrService` was deleted in MS-567 on
> purpose. This skill's only output is a recommendation.

**Usage:** `/pattern-sweep` — run it on demand.

**When to run it (pull-based — there is no automatic trigger):** the existing Slack
gate-failure trend line nudges you. `JobCompletionNotifier` posts `⚠️ gate \`X\` failed in N
runs over the last M days` when a gate fails ≥ 3× in 7 days; when you see that line, run this.

**Scope:** gate failures only (type-1). Detecting recurring `pr-quality-work` *review comments*
(type-2 — reading merged-PR comment text via `gh` and clustering it) is deliberately **out of
scope for v1** and tracked as a follow-up. Ship the cheap, bounded half first; add the
comment-scraping half only if on-demand gate sweeps prove insufficient.

---

## What "recurring gate failure" means

The same gate (`tests` / `detekt` / `compile`) fails repeatedly for the same *underlying
reason* — e.g. `detekt` keeps tripping on `MaxLineLength`, not a scattering of unrelated rules.
The `DatabasePatternDetector` already flags *that a gate* recurs; this skill reads the run
summaries to identify the *specific* cause, then recommends how to stop it recurring.

---

## Steps

### 1. List recent failed runs, window to the last 7 days

The advisor MCP server is the data source. It is a passive tool provider — **this session does
the cross-run reasoning in its own context.** Use the tools that already exist; add no new
advisor capability.

```
query_runs(status="FAILED", limit=30)
```

`query_runs` has no date filter — pull the most recent 30 and **drop rows whose `created_at` is
older than 7 days** in this session, so the window matches the Slack nudge (`≥ 3× in 7 days`).
Each row carries `failed_gate` (`tests` / `detekt` / `compile`). Group the remaining rows by
`failed_gate`; any gate with **≥ 3** failures in the window is a candidate recurrence.

### 2. Find the specific cause — server-slimmed summaries only

For each candidate gate, read the runs' root-cause summaries. Use **only** the advisor's
summary tools, which slim the transcript server-side and return a compact summary — never the
raw transcript:

```
analyze_run(job_id="<uuid>")      # START HERE — root-cause summary of the run
explain_failure(job_id="<uuid>")  # ESCALATE HERE if analyze_run is inconclusive — root cause
                                   # + proposed fix, tuned for failed runs
```

Both are **bounded regardless of run size** — the advisor slims each transcript before calling
Claude (head/tail + error/test/exit-status "signal" lines, with a whole-document head/tail trim
as a hard ceiling), and only the summary lands in this session. There is deliberately **no**
raw-transcript path here: a raw 90-turn transcript is 200k–500k+ tokens dumped straight into
context — costly and able to overflow the turn — and the summary tools already give you the
cause without that risk. If both summaries leave the exact cause ambiguous, report
*"cause unclear from summaries — inspect job `<uuid>` manually"* and move on; do not pull the
raw log to chase it.

Read enough summaries per gate to confirm the *same* cause repeats (usually 3–5). Keep the
sweep to **≤ 8 runs inspected total**; if more gates still qualify, name them and stop. If the
causes are unrelated, it is **not** a single pattern — do not merge them.

### 3. Classify each recurrence by enforceability

Decide the **strongest enforcement level the pattern can support**. Stronger is better *only
when the pattern is mechanically checkable* — never recommend a detekt rule for a judgment call.

| Level | Use when | Example |
|---|---|---|
| **detekt rule** (strongest) | The violation is statically decidable from the source AST/text with no runtime or judgment | line length, wildcard imports, a banned API call, `@RunWith` in `commonTest` |
| **test** | Deterministic but needs execution/wiring to observe | a Fake missing a new DAO override, a mapper dropping a field |
| **CLAUDE.md note** (weakest) | A judgment call that no static tool can decide | "challenge whether a convention is correct", "verify reachability before refactoring", altitude/idiom calls |

State explicitly which recurrences are **NOT statically checkable** and therefore prose-only —
that is a required part of the output, not an omission.

### 4. Output — a recommendation, full stop

Emit one block per confirmed recurrence. **This is the entire deliverable. Do not open a PR,
do not create a ticket, do not dispatch a worker, do not edit `detekt.yml` or `CLAUDE.md`.**

```
## Recurrence: <short name of the gate-failure class>

**Gate:** tests | detekt | compile
**Seen in:** MS-NNN (job <uuid>), MS-NNN (job <uuid>), …   ← the runs it came from
**Root cause:** <the specific shared cause, one or two sentences>
**Statically checkable:** yes | no
**Recommended enforcement:** detekt rule | test | CLAUDE.md note
**Why this level:** <one line — why not stronger/weaker>
**Suggested change:** <the concrete edit: which detekt rule + threshold, which test, or the
                       exact CLAUDE.md sentence to add>
```

If nothing crosses the ≥ 3 threshold, say so plainly — a clean sweep is a valid result.

### 5. Hand off to the human (the skill stops here)

State the sizing call so the human can act; **the skill does not act:**

- **Small graduation** — a `detekt.yml` toggle/threshold, or a single `CLAUDE.md` line → the
  human just makes the edit. Firing an autonomous worker for a one-liner is disproportionate.
- **Substantive graduation** — a custom detekt `Rule` class + test + registration → the human
  creates a ticket; the normal `ticket-work` worker implements it. The worker is one option,
  not the default.

Close with an explicit line: *"Advisory only — no PR opened, no worker dispatched. Your call on
which (if any) to graduate."*

---

## Roles

- **Skill = detective** — finds the pattern, drafts the recommendation
- **Human = decider** — and, for small changes, the implementer
- **`ticket-work` worker = implementer** — only when the fix is big enough to justify a job

## Guardrails

- **Advisory only.** Never open a PR, never dispatch a worker, never edit a rule file. Output
  is text.
- **No new advisor capability.** Use `query_runs`, `analyze_run`, `explain_failure` as they are.
  Cross-run reasoning happens in this session's context, not in a new tool.
- **Summaries only — never the raw transcript.** `analyze_run` / `explain_failure` are bounded
  regardless of run size; there is no raw-`fetch_transcript` path in this skill because a raw
  transcript dumps the whole run into context (a 90-turn run is 200k–500k+ tokens). If the
  summaries can't pin the cause, report it as unclear and move on.
- **7-day window.** Drop `query_runs` rows older than 7 days (it has no date filter), matching
  the Slack nudge. Keep the sweep to **≤ 8 runs inspected**.
- **Cost.** A scoped sweep (one gate, ~5 `analyze_run` calls) is roughly **$1–2**, with no
  raw-transcript blowup possible.
- **≥ 3 to count.** One-off gate failures are noise. A recurrence is the same *cause* across ≥ 3
  runs in the window.
- **Don't over-enforce.** A judgment-call pattern gets a CLAUDE.md note, never a detekt rule —
  a "correct implementation of a wrong rule" is the exact failure this pipeline already guards
  against.

## Relevant references

- Advisor tools: `advisor/src/main/kotlin/com/mediasage/advisor/tools/`
  (`QueryRunsTool`, `AnalyzeRunTool`; `explain_failure` in `ExplainFailureTool`) — register with
  `/mcp` if absent
- `agentruntime/.../service/JobCompletionNotifier.kt` — the Slack gate-failure trend line (the
  nudge); `feedback/detector/DatabasePatternDetector.kt` — the programmatic gate detector
- `detekt.yml` — target for graduated static rules
