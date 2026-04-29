# MS-97: Fix agent PR review — post "no change needed" reply to GitHub and suppress stdin warning

## What Changed

Two fixes to `AgentLaunchService` for the Level 4 PR review agent.

### 1. "No change needed" reply now posts to GitHub

**Root cause:** `PR_REVIEW_PROMPT` told the agent to "reply with '🤖 **Agent:**'" — ambiguous phrasing. The agent interpreted this as "write to stdout." But `spawnAgent` uses `redirectOutput(INHERIT)`, so stdout goes to the server console and never reaches GitHub.

The "changes needed" path worked because the agent pushes a commit directly via `git` — it doesn't rely on stdout capture.

**Fix:** Updated the prompt to explicitly instruct the agent to use `gh pr comment <number> --body '...'` when no change is needed. The PR number appears twice in the prompt (once in the preamble, once in the `gh` command), so switched to Java positional format specifiers (`%1$d`, `%2$s`, etc.) to reuse the first argument without changing the `.format(...)` call site.

### 2. stdin warning suppressed

**Root cause:** `ProcessBuilder` left stdin open (not redirected), causing the Claude CLI to wait 3 seconds for stdin data before proceeding and printing a warning.

**Fix:** Added `.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))` to the `ProcessBuilder` chain.

## Files Changed

- `server/.../service/AgentLaunchService.kt` — updated `PR_REVIEW_PROMPT`, added `/dev/null` stdin redirect
