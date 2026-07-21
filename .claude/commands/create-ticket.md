# /create-ticket — Write a well-structured Jira ticket for the MS project

Use this skill whenever the user asks to create, draft, or write a Jira ticket. It produces
**goal-driven** tickets: each ticket states *why* the work is needed (Context) and *what verifiable
done-state* it must reach (Acceptance Criteria), and nothing else. It does not tell the worker which
files to touch or how to build the change — a capable worker discovers that itself, and prescribing it
at best adds no value and at worst mis-steers. The skill's job is to size the work correctly and state
the goal honestly, not to pre-solve the implementation.

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

Use this exact structure — both sections are required, and these are the **only** two sections:

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
```

Do **not** add an Implementation Notes section and do **not** add a Relevant Files section. The worker
discovers the files and the approach on its own; a supplied file list or step-by-step plan is at best
redundant and at worst steers the worker into placement or mechanics a reviewer later flags as drift.

**AC rule — enforce strictly.** Every AC item must describe *what a user or reviewer can observe*, and
must **never** name a file path, module or package placement, function or type signature, or a
step-by-step instruction. Those are mechanics; they belong to the worker, not the ticket.

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
