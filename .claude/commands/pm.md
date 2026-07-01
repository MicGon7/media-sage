# /pm — Project management triage and next-action recommendations

Pulls live Jira ticket status, cross-references memory for context, and outputs a structured
backlog view. Detects stale file references caused by recent renames.

**Usage:**
- `/pm` — full backlog triage grouped into Do Next / Useful / Defer
- `/pm status` — only tickets currently In Progress or In Review
- `/pm next` — single best next ticket with full reasoning

---

## Steps

### 1. Determine the mode

Read the argument (if any):
- No argument or `triage` → full triage mode
- `status` → status mode
- `next` → next-action mode

### 2. Query Jira

Use `searchJiraIssuesUsingJql` with `cloudId: media-sage.atlassian.net`.

**Full triage / next-action:**
```
project = MS AND status not in (Done) ORDER BY key ASC
```
Request fields: `summary`, `status`, `labels`, `description`, `parent`.
Limit to 50. This returns both pipeline and product tickets — you will filter below.

**Status mode:**
```
project = MS AND status in ("In Progress", "In Review") ORDER BY updated DESC
```

### 3. Filter to pipeline and infrastructure tickets

Exclude product tickets (epics MS-1, MS-2, MS-3 and tickets with no pipeline relevance).
Keep tickets under MS-4 (Infrastructure) or MS-5 (Agentic Pipeline), or tickets whose
summaries clearly relate to: agentruntime, orchestrator, worker, judge, feedback scanner,
Cloud Run, Supabase, pipelineScenarios, deploy workflows, or skills/Claude Code tooling.

If unsure whether a ticket is pipeline vs product, include it — false negatives are worse.

### 4. Stale-reference detection

For each ticket in scope, scan the description for backtick-quoted file paths
(pattern: `` `path/to/file` ``). For each path found:

```bash
find . -path "./<extracted-path>" -not -path "./.git/*" 2>/dev/null
```

If the find returns no results, mark that ticket with a ⚠️ stale ref flag and note which
path is missing. This is most common after module renames (e.g. `orchestrator/` → `agentruntime/`).

Batch the finds — run them in parallel or as a single multi-path find. Do not open any files.

### 5. Categorize tickets

Assign each ticket to one of three buckets using these heuristics:

**Do Next** — any of:
- Directly improves production pipeline behavior or quality signal
- Prompt-only or config-only change (low risk, fast)
- No blockers, no dependency on another open ticket
- Has been explicitly called out as load-bearing in memory

**Useful** — any of:
- Meaningful improvement but not urgent
- Depends on a "Do Next" ticket
- Infrastructure hardening (IaC, declarative deploy)
- Test coverage gaps

**Defer** — any of:
- Blocked by an external rename or infra change not yet done
- Enhancement layered on top of a feature that doesn't exist yet
- Pure ergonomics with no production impact
- Stale — superseded by another ticket or approach

Tickets with ⚠️ stale ref flags: keep in their category but append the flag so the
user knows the description needs updating before work begins.

### 6. Output

#### Full triage mode

```
## Do Next

| Ticket | Summary | Notes |
|--------|---------|-------|
| MS-NNN | ... | one-line reasoning |

## Useful

| Ticket | Summary | Notes |
|--------|---------|-------|
| MS-NNN | ... | one-line reasoning |

## Defer

| Ticket | Summary | Notes |
|--------|---------|-------|
| MS-NNN | ... | one-line reasoning |
```

Append a stale refs summary at the end if any were found:

```
### Stale file references
- MS-NNN: `orchestrator/src/...` not found — likely renamed to `agentruntime/src/...`
```

#### Status mode

```
## In Progress
- MS-NNN — Summary (link)

## In Review
- MS-NNN — Summary (link)
```

If nothing is in progress, say so explicitly.

#### Next-action mode

Output a single recommendation block:

```
## Recommended next: MS-NNN

**Summary:** ...
**Why now:** [2-3 sentences — why this ranks above others]
**Effort:** prompt-only | config | code
**Stale refs:** none | ⚠️ `path/to/file` not found
```

---

## Context to carry in

Before categorizing, read the memory index at:
`.claude/projects/-Users-michaelgonzalez-Dev-Learn-Agentic-media-sage/memory/MEMORY.md`

Key memory files to check:
- `project_triage_july2026.md` — last known triage state and wont-do decisions
- `project_agentruntime_current_state.md` — what the agentruntime does today
- `feedback_no_ticket_refs_in_pipeline_code.md` — context for MS-415 category

Do not re-fetch memory files you already have in context. Use what's available.
