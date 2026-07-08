# /create-ticket — Write a well-structured Jira ticket for the MS project

Use this skill whenever the user asks to create, draft, or write a Jira ticket. It enforces the
required structure, prohibits tooling instructions from AC, and ensures Relevant Files is always
populated before the ticket is created.

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

### 2. Research Relevant Files

Before drafting the ticket, search the codebase for the files that a worker would need to read and
edit. Use `find`, `grep`, or the Explore agent as needed.

Rules:
- List only files a worker would actually open — source files, config, existing tests
- Never list `scripts/worker-*.sh` — those are pipeline tools, not implementation context
- If no existing files are directly relevant (e.g. the ticket creates something new), list the
  directory where the new file will live and one or two reference files to model it after

### 3. Detect module boundaries and decide ticket count

Group the Relevant Files from step 2 by bucket:

| Path prefix | Bucket |
|---|---|
| `shared/` | `:shared` |
| `composeApp/` | `:composeApp` |
| `appServer/` | `:appServer` |
| `agentruntime/` | `:agentruntime` |
| Anything else (`.github/`, `Dockerfile*`, `gradle/`, `docs/`, etc.) | `infrastructure` |

**Decision rules:**

- **Infrastructure + any module files** → create a **single `assisted` ticket** covering all files. Note in Implementation Notes that the ticket is cross-cutting. Do not decompose.
- **Infrastructure files only** → create a **single ticket**. Do not decompose.
- **Multiple Gradle modules, no infrastructure** → **multi-ticket mode**: one ticket per module, each with only its own module's Relevant Files.
- **Single Gradle module, >8 files** → split into two tickets at the natural seam:
  - `:shared` — data layer (entities, migrations, DAOs) vs. domain layer (domain models, repository interfaces, mappers)
  - `:composeApp` — **one ticket per screen**. A screen is the natural split unit. If the work touches two or more distinct screens or top-level components with no shared runtime state between them, create one ticket per screen. Exception: if one screen directly hosts the other (e.g. a detail sheet inside a list screen), keep them together.
  - Other modules — use your best judgment to find a cohesive seam
- **Single Gradle module, ≤8 files** → single ticket. Proceed to step 4.

**KMP compile dependency order (encode as Jira `Blocks` links in step 6):**

```
:appServer, :agentruntime   — independent (no dependency on client modules)
:shared                     — must compile before :composeApp
:composeApp                 — depends on :shared
```

### 4. Draft the ticket body

Use this exact structure — all four sections are required:

```
## Context

{Why this work is needed. One to three sentences describing the problem or opportunity.
Reference the root cause or trigger if known.}

## Acceptance Criteria

{Bulleted checklist. Each item must describe an observable outcome — something the user
can see, click, read, or verify by running the app. Never include tooling commands,
quality gate steps, or CI instructions here. Those belong in CLAUDE.md.}

- [ ] {observable outcome}
- [ ] {observable outcome}

## Implementation Notes

{Optional hints for the worker: patterns to follow, APIs to use, edge cases to handle,
links to reference implementations. Skip this section if there is nothing non-obvious to say.}

## Relevant Files

{Files and directories the worker should read before writing any code. One entry per line.}

- `path/to/file.kt` — {why it matters}
```

**AC rule — enforce strictly:** Every AC item must describe what a user or reviewer can observe.
Reject any item that names a tool, script, or quality gate:

| Prohibited (tooling) | Correct (observable) |
|---|---|
| Run `./gradlew detekt` | No detekt violations are introduced |
| Run `./scripts/run-affected-tests.sh` | All existing tests continue to pass |
| CI passes | The feature works end-to-end in the running app |

### 5. Determine the correct Jira fields

- **Parent epic:** use the `parent` field with the epic issue key resolved from the JQL result in step 1 (e.g. `MS-68`). If the user selected "none", omit the field entirely.
- **Label:** `assisted` or `autonomous` — set from step 1.
- **Summary:** concise imperative phrase, e.g. "Add retry logic to CloudRunJobsClient"

### 6. Create the ticket(s)

**Single-ticket mode:** Call `createJiraIssue` once:
- `cloudId`: `media-sage.atlassian.net`
- `project`: `{ "key": "MS" }`
- `issuetype`: `{ "name": "Task" }`
- `summary`: the one-line summary
- `description`: the full body from step 4, using `contentFormat: "markdown"`
- `parent`: `{ "key": "<epic key>" }` if an epic was identified
- `labels`: `["assisted"]` or `["autonomous"]`

**Multi-ticket mode:**
1. Create tickets in compile order: `:shared` before `:composeApp`; `:appServer` and `:agentruntime` can be created in any order.
2. Use the same `parent` epic and `labels` on all tickets.
3. After all tickets are created, call `createIssueLink` for each blocking relationship:
   - `cloudId`: `media-sage.atlassian.net`
   - `type`: `Blocks`
   - `inwardIssue`: the blocker ticket key (e.g. the `:shared` ticket)
   - `outwardIssue`: the blocked ticket key (e.g. the `:composeApp` ticket)

### 7. Confirm to the user

Reply with:
- The Jira issue key and URL (e.g. [MS-370](https://media-sage.atlassian.net/browse/MS-370))
- A one-sentence plain-English description of what the ticket will deliver (6th-grade level)
- Any AC items you were uncertain about — flag them so the user can review before work starts
