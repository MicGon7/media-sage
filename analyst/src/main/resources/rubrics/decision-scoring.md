# Decision Scoring Rubric — v2

Criteria for evaluating Claude Code worker session quality. Score each criterion 1–5 based on
the overall session transcript. Scores reflect patterns across the full session, not a single turn.

---

## tool_choice

Evaluates whether the agent selected the most appropriate tool for each task — surgical reads,
targeted searches, and minimal edits rather than broad scans or unnecessary fetches.

- **5** — Always uses the minimal, correct tool. Reads only files it will act on. Grepping is
  targeted. Edits are surgical with no unnecessary surrounding changes.
- **4** — Mostly appropriate tool choices with one or two minor inefficiencies (e.g. one extra read).
- **3** — Adequate but with several tool choices that could be more targeted (e.g. grep from root
  when a specific path was known).
- **2** — Frequent mismatches: reads files that aren't needed, broad searches when targeted ones
  would work, or writes files it immediately overwrites.
- **1** — Consistently poor tool choices that waste turns or produce incorrect results.

---

## tool_efficiency

Evaluates whether the agent used the minimum number of tool calls to accomplish each task, with
no redundant fetches or repeat calls that return the same information.

- **5** — Minimum calls needed; no redundant fetches. Each call produces new information acted on.
- **4** — One redundant call that does not affect the outcome.
- **3** — Several redundant calls but no wasted turns caused by the redundancy.
- **2** — Frequent redundant calls that create wasted turns or slow progress noticeably.
- **1** — Repetitive looping calls with no progress; agent re-fetches the same resources repeatedly.

---

## retry_recovery

Evaluates how the agent handles errors, unexpected output, and failures — whether it diagnoses
the root cause correctly and recovers precisely rather than looping on the same approach.

- **5** — Errors are diagnosed correctly on first attempt; recovery is targeted and resolves the
  root cause. No repeated attempts with the same fix.
- **4** — Good recovery with one unnecessary retry or slightly imprecise initial diagnosis.
- **3** — Recovery eventually succeeds but requires multiple attempts or tries a redundant fix
  before finding the right one.
- **2** — Agent applied a fix, local gates passed, but the fix targeted the wrong layer or file
  and CI (or equivalent) later proved the issue unresolved — agent shipped with false confidence.
- **1** — Does not recover meaningfully; stops, spirals, or escalates without attempting root-cause
  diagnosis.

---

## context_management

Evaluates how the agent manages information across turns — reads the right things at the right
time, avoids redundant fetches, and acts on current state rather than stale context.

- **5** — Reads each file once and acts on the information without re-fetching. Context accurately
  reflects current code state throughout the session.
- **4** — Minor redundancy (one re-read) but context stays accurate and no incorrect actions taken.
- **3** — Some redundant tool calls or mild context drift, but no incorrect actions caused by stale
  information.
- **2** — Multiple redundant fetches; acts on stale context in at least one turn causing a wasted
  action.
- **1** — Significant context confusion: contradicts earlier findings, re-fetches repeatedly, or
  takes actions based on information that was already superseded.

---

## scope_adherence

Evaluates whether the agent stayed within the ticket scope — only touching files and making
changes that were explicitly requested or are clearly necessary to fulfil the acceptance criteria.

- **5** — Only ticket-specified files touched; every change directly fulfils an acceptance
  criterion or is an unavoidable side-effect of it.
- **4** — Minor adjacent cleanup that is clearly justified and does not stray from the intent of
  the ticket.
- **3** — One unrequested change (e.g. a refactor or style fix in a file the ticket did not
  mention).
- **2** — Multiple unrequested changes across one or more files not in scope.
- **1** — Substantial unrequested work, or files well outside ticket scope modified.
