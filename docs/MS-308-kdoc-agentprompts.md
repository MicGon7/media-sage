# MS-308: Add KDoc to AgentPrompts

## What was done

Confirmed and retained the KDoc comment at the top of
`agent/src/main/kotlin/com/mediasage/agent/service/AgentPrompts.kt`.

The comment documents:
- The file's purpose (bootstrap prompt templates dispatched per job type)
- The three-part model (CLAUDE.md → rules, Prompt → context, Skill → instructions)
- The relationship to `AgentLaunchService`, which is the caller

## Pattern learned

`AgentPrompts.kt` uses top-level `internal val` properties rather than an `object` wrapper. When a
file contains only related top-level vals, a floating KDoc block placed immediately after the
package declaration serves as the effective file-level documentation. Kotlin treats a `/** */`
comment before the first declaration as that declaration's doc, but in practice readers see it as
file-level documentation for a tightly cohesive set of constants.

The three-part model (CLAUDE.md / Prompt / Skill) is the core mental model for the autonomous
pipeline — having it in the source file ensures it is discoverable from the IDE without needing to
read the architecture docs.

## Files changed

- `agent/src/main/kotlin/com/mediasage/agent/service/AgentPrompts.kt` — KDoc already present; no
  code change required
- `docs/MS-308-kdoc-agentprompts.md` — this learning doc
