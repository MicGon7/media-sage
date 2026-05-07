# MS-84: Containerize :agent and Deploy to Railway

## What we built

Moved the `:agent` orchestration server from a laptop/ngrok setup into a Docker container deployed to Railway, giving it a stable public URL for Jira and GitHub webhooks.

## Key decisions

### Multi-stage Dockerfile
Used a two-stage build: JDK 21 (Gradle build) → JRE 21 runtime. The runtime stage installs Node.js and the Claude Code CLI, then adds a non-root `agent` user. The non-root user is required by Anthropic — `--dangerously-skip-permissions` refuses to run as root.

### entrypoint.sh clones the repo at startup
The container clones (or pulls) the media-sage repo using the bot account PAT on every startup. This means the container always has a fresh copy of main without needing to bake the repo into the image. The `AGENT_REPO_PATH` env var tells `AgentLaunchService` where the working copy lives.

### Bot account as machine user
`media-sage-bot` is a dedicated GitHub machine user with Write access and a PAT scoped to `repo` + `workflow`. All git operations inside the container run as this user. This keeps the bot's commits and PRs clearly attributed and separate from the human developer's account.

### mcp-atlassian uses API token auth inside the container
The container cannot use OAuth browser flows. mcp-atlassian is configured with `JIRA_EMAIL` + `JIRA_API_TOKEN` env vars, which use HTTP Basic auth against the Atlassian REST API directly.

### --output-format stream-json --verbose
Claude Code is invoked with `--output-format stream-json --verbose` so the JVM can pipe each output line through the logger in real time. This makes Railway's log viewer useful for confirming the agent is alive and progressing. The tradeoff is high log volume — every token event is a separate JSON line.

### railway.toml isolates :agent service config
`agent/railway.toml` pins the build to `agent/Dockerfile` and sets the health check path and port. This keeps `:agent` config separate from `:server`'s `railway.toml` in the root.

## What we learned

- `--dangerously-skip-permissions` hard-blocks on root. The non-root user requirement is not optional and must be in the Dockerfile, not worked around.
- Railway's health check must pass before traffic is routed. `GET /health` returning 200 is a hard dependency for deployment to succeed.
- stream-json logging is useful for liveness but too noisy for analysis. A structured milestone-logging layer on top of the raw subprocess output would make Railway logs actionable (filed as MS-148).
- The stable Railway URL eliminates the entire ngrok lifecycle: no URL changes on restart, no laptop dependency, no manual webhook re-registration.

## End-to-end flow (production)

1. Human assigns ticket to `media-sage-bot` and transitions to In Progress (future: MS-147)
2. Jira webhook fires `POST /webhook/jira` on Railway
3. `AgentLaunchService` spawns `claude -p "..." --dangerously-skip-permissions` as a subprocess
4. Claude Code clones the repo, does the work, commits, pushes, opens a PR
5. Human reviews the PR — submits **Request Changes** to trigger fixes or **Approve** to merge
6. GitHub webhook fires `POST /webhook/github` on Railway
7. Agent addresses feedback and pushes a fix commit

## Files added / changed

- `agent/Dockerfile` — multi-stage build
- `agent/entrypoint.sh` — repo clone/pull + server start
- `agent/railway.toml` — Railway service config for `:agent`
- `agent/src/.../routes/HealthRoutes.kt` — `GET /health`
- `CLAUDE.md` — container deployment docs, env var table, stable URL section
