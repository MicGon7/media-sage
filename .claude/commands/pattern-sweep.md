# /pattern-sweep — Surface recurring worker mistakes and recommend how to enforce them

Reads recent pipeline runs and quality reviews, finds the same *class* of mistake repeating
across runs, and — for each recurrence — recommends **how strongly to enforce it**: a
`CLAUDE.md` prose note (weakest), a test, or a `detekt` rule (strongest, statically-checkable
patterns only).

This is the surviving, advisory-only half of the retired learning loop (MS-567). It is a
**detective, not an implementer**: it never opens a PR, never dispatches a worker, and never
edits a rule. The human is the decider — and, for small changes, the implementer.

> ⚠️ Do **not** resurrect the auto-PR path. `FeedbackPrService` was deleted in MS-567 on
> purpose. This skill's only output is a recommendation.

**Usage:**
- `/pattern-sweep` — full sweep: both gate-failure and review-comment recurrences
- `/pattern-sweep gates` — only type-1 (gate-failure) recurrences
- `/pattern-sweep reviews` — only type-2 (review-comment) recurrences

**When to run it (pull-based — there is no automatic trigger for this skill):**
- **Type-1 (gate failures)** are *nudged* by the existing Slack gate-failure trend line
  (`JobCompletionNotifier` posts `⚠️ gate \`X\` failed in N runs over the last M days` when a
  gate fails ≥ 3× in 7 days). When you see that line, run `/pattern-sweep gates`.
- **Type-2 (review comments)** have no nudge and need none — you are already in the PR. Run
  `/pattern-sweep reviews` when a `pr-quality-work` comment feels familiar. Do **not** build a
  type-2 push trigger.

---

## What "recurring pattern" means

The same *class* of mistake showing up across multiple runs, in two flavors:

1. **Gate-failure recurrence** — the same gate (`tests` / `detekt` / `compile`) fails
   repeatedly for the same underlying reason (e.g. `MaxLineLength` keeps tripping). The
   `DatabasePatternDetector` already flags *that a gate* recurs; this skill reads the
   transcripts to identify the *specific* cause.
2. **Review-comment recurrence** — the `pr-quality-work` reviewer keeps leaving the same kind
   of comment (e.g. hardcoded UI strings, `expect/actual` for build config, a null guard in a
   `SideEffect`). These runs **succeed**, so no gate flags them — only reading across recent
   quality-review PRs surfaces them.

---

## Steps

### 1. Determine the mode

Read the argument (if any):
- No argument or `all` → both types
- `gates` → type-1 only (skip step 3)
- `reviews` → type-2 only (skip step 2)

### 2. Type-1: gate-failure recurrences (advisor MCP)

The advisor MCP server is the data source for gate failures and transcripts. It is a passive
tool provider — **this session does the cross-run reasoning in its own context.** Do not add
any new advisor capability; use the tools that already exist.

1. List recent failed runs:
   ```
   query_runs(status="FAILED", limit=30)
   ```
   Each row carries `failed_gate` (`tests` / `detekt` / `compile`) — that tells you *which*
   gate, not *why*. Group the rows by `failed_gate`. Any gate with ≥ 3 failures in the window
   is a candidate recurrence.

2. For each candidate gate, read the transcripts of its failing runs to find the *specific*
   shared cause:
   ```
   fetch_transcript(job_id="<uuid>")   # raw JSONL — read the gate output directly
   analyze_run(job_id="<uuid>")        # Claude-summarized root cause when the raw log is large
   ```
   Read 3–5 transcripts per gate — enough to confirm the *same* underlying cause repeats
   (e.g. every `detekt` failure is `MaxLineLength`, not a scattering of unrelated rules). If
   the causes are unrelated, it is **not** a single pattern — do not merge them.

### 3. Type-2: review-comment recurrences (GitHub via `gh`)

`pr-quality-work` posts its findings as GitHub PR reviews with state `COMMENT`, summary
prefixed `🤖 **Agent:**`. **GitHub is the source of truth — read it live.** Do **not** store a
copy of the comments in Supabase; that duplicates GitHub and adds a drift-prone write path.
(The completion event already records `reviewCommentCount` per run, but not the text — the
text stays on GitHub.)

1. List recently merged/closed PRs and pull their agent review comments:
   ```bash
   # recent PRs
   gh pr list --state all --limit 30 --json number,title,mergedAt

   # agent review bodies + inline comments for one PR
   gh api "/repos/$GITHUB_OWNER/$GITHUB_REPO/pulls/<n>/reviews" \
     --jq '.[] | select(.body | startswith("🤖 **Agent:**")) | .body'
   gh api "/repos/$GITHUB_OWNER/$GITHUB_REPO/pulls/<n>/comments" \
     --jq '.[] | {path, line, body}'
   ```
   Use `$GITHUB_OWNER` / `$GITHUB_REPO` from the environment; fall back to
   `michael-gonzalez-dev` / `media-sage` if unset.

2. Cluster the comment bodies by the *kind* of issue they raise (hardcoded strings, wrong
   effect type, non-idiomatic Kotlin, reused-helper-missed, …). A cluster of ≥ 3 comments
   across ≥ 2 distinct PRs is a recurrence worth reporting. One-off comments are noise — drop
   them.

### 4. Classify each recurrence by enforceability

For every confirmed recurrence, decide the **strongest enforcement level it can support**.
Stronger is better *only when the pattern is mechanically checkable* — never recommend a
detekt rule for a judgment call.

| Level | Use when | Example |
|---|---|---|
| **detekt rule** (strongest) | The violation is statically decidable from the source AST/text with no runtime or judgment | line length, wildcard imports, a banned API call, `@RunWith` in `commonTest` |
| **test** | Deterministic but needs execution/wiring to observe | a Fake missing a new DAO override, a mapper dropping a field |
| **CLAUDE.md note** (weakest) | A judgment call that no static tool can decide | "challenge whether a convention is correct", "verify reachability before refactoring", altitude/idiom calls |

State explicitly which recurrences are **NOT statically checkable** and therefore prose-only —
that is a required part of the output, not an omission.

### 5. Output — a recommendation, full stop

Emit one block per confirmed recurrence. **This is the entire deliverable. Do not open a PR,
do not create a ticket, do not dispatch a worker, do not edit `detekt.yml` or `CLAUDE.md`.**

```
## Recurrence: <short name of the mistake class>

**Type:** gate-failure | review-comment
**Seen in:** MS-NNN (job <uuid>), PR #NN, PR #NN   ← runs/PRs it came from
**Root cause:** <the specific shared cause, one or two sentences>
**Statically checkable:** yes | no
**Recommended enforcement:** detekt rule | test | CLAUDE.md note
**Why this level:** <one line — why not stronger/weaker>
**Suggested change:** <the concrete edit: which detekt rule + threshold, which test, or the
                       exact CLAUDE.md sentence to add>
```

If nothing crosses the ≥ 3 threshold, say so plainly — a clean sweep is a valid result.

### 6. Hand off to the human (the skill stops here)

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
- **No new advisor capability.** Use `query_runs`, `fetch_transcript`, `analyze_run` as they
  are. Cross-run reasoning happens in this session's context, not in a new tool.
- **No Supabase copy of review comments.** Read them live from GitHub via `gh`.
- **≥ 3 to count.** One-off gate failures and one-off review comments are noise. A recurrence
  is the same *cause* (type-1) or same *issue class* across ≥ 2 PRs (type-2), seen ≥ 3 times.
- **Don't over-enforce.** A judgment-call pattern gets a CLAUDE.md note, never a detekt rule —
  a "correct implementation of a wrong rule" is the exact failure this pipeline already guards
  against.

## Relevant references

- Advisor tools: `advisor/src/main/kotlin/com/mediasage/advisor/tools/`
  (`QueryRunsTool`, `FetchTranscriptTool`, `AnalyzeRunTool`) — register with `/mcp` if absent
- `agentruntime/.../service/JobCompletionNotifier.kt` — the Slack gate-failure trend line
  (type-1 nudge); `feedback/detector/DatabasePatternDetector.kt` — the programmatic gate detector
- `.claude/commands/pr-quality-work.md` — the reviewer whose comments type-2 reads
- `detekt.yml` — target for graduated static rules
