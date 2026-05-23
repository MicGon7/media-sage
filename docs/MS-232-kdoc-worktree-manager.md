# MS-232: KDoc for WorktreeManager

## What changed

Added KDoc comments to `WorktreeManager` (interface) and `DefaultWorktreeManager` (implementation)
in `agent/src/main/kotlin/com/mediasage/agent/service/WorktreeManager.kt`.

## Why

`WorktreeManager` is a core agent infrastructure type with no prior documentation. The interface
contract — especially the `createWorktree` fallback behaviour and the `buildAgentProcess` output
format — is not obvious from the signatures alone.

## Key details captured in the docs

- `WorktreeManager` interface: isolates concurrent agent runs via separate git worktrees.
- `createWorktree`: returns `false` (not an exception) on failure so callers can fall back to the
  main repo path gracefully.
- `buildAgentProcess`: starts `claude -p` with `--dangerously-skip-permissions` and
  `--output-format stream-json`; the caller streams structured JSON output.
- `DefaultWorktreeManager`: production implementation backed by `git worktree` shell commands.
