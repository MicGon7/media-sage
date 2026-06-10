# /create-ticket — Write a well-structured Jira ticket for the MS project

Use this skill whenever the user asks to create, draft, or write a Jira ticket. It enforces the
required structure, prohibits tooling instructions from AC, and ensures Relevant Files is always
populated before the ticket is created.

---

## Steps

### 1. Gather the inputs

If the user has not already provided them, ask:

- **What does this ticket accomplish?** (one sentence — becomes the summary)
- **Which epic does it belong to?** MS-1 (Server API), MS-2 (Shared Data), MS-3 (App UI), MS-4 (Infrastructure), MS-5 (Agentic Pipeline), or none
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

### 3. Draft the ticket body

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

### 4. Determine the correct Jira fields

- **Parent epic:** use the `parent` field with the epic issue key (e.g. `MS-4`). If no epic applies, omit the field.
- **Label:** `assisted` or `autonomous` — set from step 1.
- **Summary:** concise imperative phrase, e.g. "Add retry logic to CloudRunJobsClient"

### 5. Create the ticket

Call `createJiraIssue` with:
- `cloudId`: `media-sage.atlassian.net`
- `project`: `{ "key": "MS" }`
- `issuetype`: `{ "name": "Task" }`
- `summary`: the one-line summary
- `description`: the full body from step 3, using `contentFormat: "markdown"`
- `parent`: `{ "key": "<epic key>" }` if an epic was identified
- `labels`: `["assisted"]` or `["autonomous"]`

### 6. Confirm to the user

Reply with:
- The Jira issue key and URL (e.g. [MS-370](https://media-sage.atlassian.net/browse/MS-370))
- A one-sentence plain-English description of what the ticket will deliver (6th-grade level)
- Any AC items you were uncertain about — flag them so the user can review before work starts
