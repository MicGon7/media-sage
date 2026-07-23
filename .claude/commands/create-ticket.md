# /create-ticket — Write a well-structured Jira ticket for the MS project

Use this skill whenever the user asks to create, draft, or write a Jira ticket. Every ticket leads
with the goal — *why* the work is needed (Context) and *what verifiable done-state* it must reach
(Acceptance Criteria). Because authoring a good ticket already requires exploring the codebase (to
size the work, write honest AC, and verify technical claims), the skill also **captures that
discovery** — the files the change touches and any non-obvious constraints — as `Relevant Files` and
`Implementation Notes`. These are a **verified starting point the worker confirms, not a prescription
it obeys**: handing over the files the author already found spares the worker from re-discovering them
from scratch, while the goal (Context + AC) stays the source of truth. Do not invent mechanics the
discovery did not surface, and never let the hint stand in for a clear goal.

---

## Steps

### 1. Gather the inputs

If the user has not already provided them, ask:

- **What does this ticket accomplish?** (one sentence — becomes the summary)
- **Which epic does it belong to?** Before asking, run the following JQL to fetch current open epics:
  ```
  project = MS AND issuetype = Epic AND statusCategory != Done ORDER BY created DESC
  ```
  Present the returned epic names to the user as a numbered list, always appending "none" as a final option. If the query fails or returns no results, ask the user to provide the epic key manually.
- **Automation mode:** `assisted` (human drives, AI helps) or `autonomous` (AI executes end-to-end with no human in the loop)

If the user has given you enough context to infer any of these, derive them — don't ask for what
you can figure out.

### 2. Size the work — decide ticket count by independently-shippable goal

Right-sizing is the one structural decision this skill makes. The seam is the **independently
shippable, independently verifiable outcome** — never the code layer or the Gradle module.

**Default to a single ticket.** Split only when the work contains two or more outcomes that each ship
*and* verify on their own. Deciding this needs no file survey — you count distinct user-facing (or
interface-observable) outcomes, which is a Context/AC-altitude judgment.

**The test for a valid split: does each piece have its own observable AC?**

- **Split by vertical slice, never by layer or module.** A "data only" or "UI only" half is dead code
  until its partner lands — nobody can observe it in the running app, so it has no honest AC and is not
  a valid ticket. If a feature is too big for one ticket, cut it into *thinner slices that each still
  work end-to-end*.
  - ✅ Valid: "Voices list shows each figure's name and portrait" → then "Voices list supports search."
    Each is thin but whole — data + UI + a verifiable outcome.
  - ❌ Invalid: "Voices data layer" + "Voices UI." Neither is verifiable alone.
- **A backend-only ticket is legitimate only when the endpoint *is* the observable outcome.** "`GET
  /figures/{id}` returns the figure's bio" stands alone because you can verify it by calling the
  endpoint. That is a real independent outcome, not a layer split.

**Ordering between split tickets (encode as Jira `Blocks` links in step 5):** add a `Blocks` link only
when one *goal* genuinely depends on another shipping first (feature B builds on feature A's shipped
behavior). Never add a `Blocks` link to encode compile order or module dependency — a single vertical
slice compiles as a unit within one PR, so compile order is not a reason to split or sequence tickets.

### 3. Draft the ticket body

Use this exact structure. **Context** and **Acceptance Criteria** are the goal — required and
authoritative. **Implementation Notes** and **Relevant Files** capture the discovery you did while
authoring and are handed to the worker as hints:

```
## Context

{Why this work is needed. One to three sentences describing the problem or opportunity.
Reference the root cause or trigger if known. This is the goal's "why".}

## Acceptance Criteria

{Bulleted checklist. Each item must describe an observable outcome — something a user or
reviewer can see, click, read, or verify by running the app or calling an interface. This
is the goal's verifiable definition of done.}

- [ ] {observable outcome}
- [ ] {observable outcome}

## Implementation Notes

{Non-obvious constraints, patterns to follow, or edge cases you surfaced while researching —
at the intent/constraint altitude, never a step-by-step plan. Omit this section entirely if
nothing non-obvious came up.}

## Relevant Files

{The files the change touches, as best you could determine — the discovery you already did,
handed forward so the worker need not repeat it. One per line with a short note on why each
matters. This is a verified starting point, NOT an exhaustive or binding list: the worker
confirms each path against current code and discovers anything you missed.}

- `path/to/File.kt` — {why it matters}
```

**Research the Relevant Files before drafting — you are exploring the codebase anyway.** Verifying the
technical claims your Context/AC depend on (see "Verify before you assert" below) already takes you into
the code; record what you find rather than throw it away. `grep`/`Glob`/`Read` for the files the change touches: the obvious target plus the co-located
files a change fans out to — a ViewModel's Contract and Screen; a DAO's entity, repository, and every
`commonTest` Fake; a new component's nearest sibling and its test. List only files a worker would open;
**never** list `scripts/worker-*.sh` (pipeline tools, not implementation context). For net-new work with
no existing file, name the directory the new file will live in and one reference file to model it after.
Keep it best-effort — do not exhaustively trace every transitive reference; the worker verifies and fills
gaps. Both hint sections are optional: omit them only when authoring genuinely surfaced nothing useful.

**AC rule — enforce strictly.** Every AC item must describe *what a user or reviewer can observe*, and
must **never** name a file path, module or package placement, function or type signature, or a
step-by-step instruction. Those are mechanics — they belong in `Implementation Notes` or `Relevant
Files`, never in AC.

| Prohibited | Correct (observable) |
|---|---|
| Run `./gradlew detekt` | No detekt violations are introduced |
| Run `./scripts/run-affected-tests.sh` | All existing tests continue to pass |
| CI passes | The feature works end-to-end in the running app |
| Add `bio: String` to `FigureDto` | The figure profile displays a short biography |
| Put the mapper in `data/mapper/` | The figure's biography renders on the Voices detail sheet |

**Verify before you assert — no unverified technical claims.** Any Context sentence or AC item that
names a specific symbol, field, DTO, endpoint, function, or behaviour — and that the work depends on —
is a *claim about the codebase*, not a given. Confirm it with `grep`/`Read` before you write it into
the ticket. If you cannot confirm the mechanism exists, or it behaves differently than assumed, do not
assert it: state the desired outcome and name the gap honestly instead (e.g. "today the encourage
response carries no figure identifier — surfacing one is part of this work"). A wrong technical claim
in a ticket propagates through the worker and every quality gate unchecked — this is exactly how MS-87
(PR #506) shipped a fragile `figureId` workaround built on a field that did not exist on the DTO it
named. Note the discipline is about *not asserting false facts*, not about prescribing the fix: state
the true outcome you want and let the worker discover the path.

### 4. Determine the correct Jira fields

- **Parent epic:** use the `parent` field with the epic issue key resolved from the JQL result in step 1 (e.g. `MS-68`). If the user selected "none", omit the field entirely.
- **Label:** `assisted` or `autonomous` — set from step 1.
- **Summary:** concise imperative phrase, e.g. "Add retry logic to CloudRunJobsClient"

### 5. Create the ticket(s)

**Single-ticket mode:** Call `createJiraIssue` once:
- `cloudId`: `media-sage.atlassian.net`
- `project`: `{ "key": "MS" }`
- `issuetype`: `{ "name": "Task" }`
- `summary`: the one-line summary
- `description`: the full body from step 3, using `contentFormat: "markdown"`
- `parent`: `{ "key": "<epic key>" }` if an epic was identified
- `labels`: `["assisted"]` or `["autonomous"]`

**Multi-ticket mode** (only when step 2 found two or more independently-shippable goals):
1. Use the same `parent` epic and `labels` on all tickets.
2. After all tickets are created, call `createIssueLink` for each genuine goal dependency:
   - `cloudId`: `media-sage.atlassian.net`
   - `type`: `Blocks`
   - `inwardIssue`: the blocker ticket key (the goal that must ship first)
   - `outwardIssue`: the blocked ticket key (the goal that builds on it)

### 6. Confirm to the user

Reply with:
- The Jira issue key and URL (e.g. [MS-370](https://media-sage.atlassian.net/browse/MS-370))
- A one-sentence plain-English description of what the ticket will deliver (6th-grade level)
- Any AC items you were uncertain about — flag them so the user can review before work starts
