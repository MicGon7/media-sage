# MS-598 — Consolidate pipeline Claude model config

## Problem

The Claude model each pipeline role used was declared in ~7 disconnected places, and the values
had drifted:

- `build-worker-image.yml` set `CLAUDE_MODEL=claude-sonnet-4` **and** `ANTHROPIC_MODEL=claude-sonnet-4`.
- `deploy-orchestrator.yml` set `ANTHROPIC_MODEL=claude-sonnet-4`.
- `application.conf`, `AgentConfig.kt`, and `Application.kt` each defaulted to `claude-sonnet-4-6`.
- `AnalyzeRunTool.kt` and `ExplainFailureTool.kt` hardcoded `const val MODEL = "claude-sonnet-4-6"`
  and ignored the environment entirely.

Bumping the model meant editing several files and risking drift between them.

## What changed

Every pipeline role now runs `claude-sonnet-5`, and the config is consolidated:

- **`CLAUDE_MODEL` was deleted.** It was a homegrown duplicate of the standard `ANTHROPIC_MODEL`
  (the var Claude Code and the Anthropic SDK read natively). The worker entrypoint and the metrics
  reporter now read `ANTHROPIC_MODEL`.
- **No source file hardcodes a model string.** The advisor tools take `model` as a parameter, fed
  from `ANTHROPIC_MODEL` at registration, falling back to `DEFAULT_CLAUDE_MODEL`.
- **One shared fallback default** — `pipelineCore/…/ClaudeModel.kt` → `DEFAULT_CLAUDE_MODEL`,
  consumed by both `:agentruntime` and `:advisor` (the two JVM modules that depend on `:pipelineCore`).
  The dead `claude-sonnet-4-6` defaults in `application.conf` / `AgentConfig` / `Application` were
  removed or pointed at this constant.

## The model-config model (how to reason about it)

There are three **independent** production knobs — one per runtime — plus one shared safety default:

| Knob | Controls | Where it lives |
|---|---|---|
| `ANTHROPIC_MODEL` literal | Ticket worker (coding agent) | `build-worker-image.yml` |
| `ANTHROPIC_MODEL` literal | AgentRuntime AC judge | `deploy-orchestrator.yml` |
| `-e ANTHROPIC_MODEL` at registration | Advisor MCP | `claude mcp add …` command |
| `DEFAULT_CLAUDE_MODEL` | Fallback only, when a knob above is unset | `pipelineCore/…/ClaudeModel.kt` |

Key point: the three knobs are **independent**. The judge can run a different (e.g. cheaper) model
than the worker — just set different literals in the two workflows. `DEFAULT_CLAUDE_MODEL` does *not*
couple them; a deployed service's env-var literal always wins over the constant. The constant only
supplies a model when the env var is absent (local dev, and the advisor if you don't pass `-e`).

## Decisions considered and rejected

- **GitHub repo variables (`vars.PIPELINE_*_MODEL`) for the CI values.** Rejected. A repo variable
  lives in GitHub settings, not git (weaker audit trail), needs a manual setup step, and an unset
  variable would push a blank model — forcing a `|| 'fallback'` guard. Since Cloud Run needs a
  redeploy to pick up a model change *either way*, the variable bought no redeploy savings. Plain
  literals in the workflow YAMLs keep the value in git and drop the guard.
- **Collapsing the JVM services onto one shared source (judge falls back to the constant).**
  Rejected. It would couple the judge and advisor and remove the ability to run the judge on a
  different model than the worker. Per-service independence is the goal.
- **Re-hardcoding `const val MODEL = "claude-sonnet-5"` in the advisor.** Rejected — reintroduces
  the drift and the two-copy hardcoded-constant problem this ticket exists to remove, and violates
  the acceptance criteria.

## Follow-up (not yet actionable)

The advisor reads `ANTHROPIC_MODEL` as *optional* (falls back to `DEFAULT_CLAUDE_MODEL`) while its
other three env vars are required. That's deliberate for a hand-registered local stdio MCP. Revisit
if/when the advisor is ever deployed to the cloud — at that point making the model an explicit,
required, deploy-declared value would match the worker/orchestrator pattern.

## Verification

- `./gradlew :pipelineCore:test :agentruntime:test :advisor:test` and the corresponding `detekt`
  tasks pass.
- `claude-sonnet-5` confirmed serving via the Fuelix proxy (`https://api.fuelix.ai`) with the
  `ANTHROPIC_AUTH_TOKEN` bearer token. Fuelix exposes no Opus model to this org (`claude-opus-4-5`
  returns 403), so Sonnet 5 is the strongest model available to the pipeline.
- End-to-end (one worker run + one advisor call on Sonnet 5) is a post-deploy check.
