# MS-195 — Agent Briefing

## Problem

Cloud Run workers were spending 10–20 turns exploring the codebase before doing any real work. MS-167 (a single-file change) used 44 turns and 3M cache-read tokens ($1.27) — most of that was the worker figuring out which files to look at and which patterns to follow.

## Solution

Added `AgentBriefing` — a service that runs a bounded `claude -p` call in the orchestrator before dispatching to Cloud Run. It prepares a concise briefing (relevant files, existing pattern, CLAUDE.md constraints) and appends it to the worker prompt under an `## Agent Briefing` section.

## How it works

```
Jira webhook → orchestrator → AgentBriefing.prepare() → enriched prompt → Cloud Run worker
```

**`AgentBriefing.prepare()`:**
- Runs `claude -p "<briefing prompt>" --max-turns 3 --output-format text` from `AGENT_REPO_PATH`
- 60-second timeout — process is force-killed if exceeded
- Returns `""` on any failure (timeout, non-zero exit, claude not found)
- Dispatch always proceeds — briefing failure is never a blocker

**Prompt asks for three things only:**
1. 3–5 most relevant files to read first
2. Existing pattern to follow (with file path example)
3. CLAUDE.md constraints specifically relevant to this task

**`AgentLaunchService`** appends the briefing to the worker prompt:
```
## Agent Briefing
<briefing output>
```

**`AgentModule`** wires up `AgentBriefing` only when `useCloudRunWorkers = true` — local process dispatch is unaffected.

## Intelligence seam

The `JobDispatcher` interface in `CloudRunJobsClient` was intentionally left as an intelligence seam for this enhancement. `AgentBriefing` sits upstream of dispatch in `AgentLaunchService`, keeping `CloudRunJobsClient` focused on job execution only.

## Key Learnings

- **Name things for what they are, not what they do mechanically.** `PromptEnricher` describes a mechanism; `AgentBriefing` describes the concept — a manager briefing a contractor before they start work.
- **Fail open, never block.** The briefing runs on a best-effort basis. If `claude` is not on PATH, the process times out, or the output is empty — the worker gets the original prompt. The agent pipeline must never be blocked by an enhancement.
- **Bounded turns matter.** `--max-turns 3` caps the briefing call so it can't spiral into a long exploration session of its own. The briefing should be a quick scan, not another full agent run.
- **Only wire up where it makes sense.** `AgentBriefing` is only instantiated when `useCloudRunWorkers = true`. Local process dispatch doesn't benefit since the worker has direct filesystem access anyway.
