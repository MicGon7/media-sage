# :advisor — Advisor MCP Server

Stdio MCP server that gives interactive pipeline run analysis. Claude Code connects to it
as a local process and can query the Supabase `jobs` and `transcripts` tables and call
Claude for analysis without leaving the conversation.

## Transport

`stdio` — Claude Code launches the server as a child process and communicates via
stdin/stdout. **Never write to stdout from application code.** All logging goes to stderr
via SLF4J + logback (`logback.xml` uses `<target>System.err</target>`).

## Environment variables (required at runtime)

| Variable             | Purpose                          |
|----------------------|----------------------------------|
| `SUPABASE_DB_URL`    | JDBC URL for Supabase Postgres   |
| `ANTHROPIC_AUTH_TOKEN` | Anthropic API key              |
| `ANTHROPIC_BASE_URL` | e.g. `https://api.anthropic.com` |

## Tools exposed

| Tool              | Description                                                          |
|-------------------|----------------------------------------------------------------------|
| `query_runs`      | List recent jobs; filter by ticket key, status, or limit            |
| `fetch_transcript`| Return raw JSONL transcript for a job                               |
| `analyze_run`     | Claude-powered turn efficiency analysis of a transcript             |
| `compare_runs`    | Side-by-side comparison of two jobs (cost, turns, duration, status) |
| `explain_failure` | Claude-powered root cause + proposed fix for a failed job           |

## Package layout

```
advisor/src/main/kotlin/com/mediasage/advisor/
├── AdvisorServer.kt     — main(), server wiring, env var loading
├── AdvisorDatabase.kt   — Exposed DB connection
├── AnthropicApi.kt      — API constants (version header, token budgets)
├── ClaudeCall.kt        — HTTP POST to /v1/messages with retry
└── tools/
    ├── QueryRunsTool.kt
    ├── FetchTranscriptTool.kt
    ├── AnalyzeRunTool.kt
    ├── CompareRunsTool.kt
    └── ExplainFailureTool.kt
```

## Adding a tool

1. Create `tools/MyTool.kt` with an extension function `Server.registerMyTool(...)`.
2. Call it from `AdvisorServer.kt` before `server.createSession(transport)`.
3. Detekt limits: `LongMethod` 30 lines, `ReturnCount` 4. Extract handler body to a private `suspend fun`.

## Packaging

`./gradlew :advisor:shadowJar` → `advisor/build/libs/advisor.jar`

## Registering with Claude Code

Add to `.claude/settings.json` (project-level) or `~/.claude/settings.json` (global):

```json
{
  "mcpServers": {
    "advisor": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/media-sage/advisor/build/libs/advisor.jar"],
      "env": {
        "SUPABASE_DB_URL": "${SUPABASE_DB_URL}",
        "ANTHROPIC_AUTH_TOKEN": "${ANTHROPIC_AUTH_TOKEN}",
        "ANTHROPIC_BASE_URL": "${ANTHROPIC_BASE_URL}"
      }
    }
  }
}
```

The env vars are expanded from the shell environment at launch — never put literal secrets
in the settings file.

After updating the config, restart Claude Code (or reload MCP servers with `/mcp`) so the
new server is picked up.
