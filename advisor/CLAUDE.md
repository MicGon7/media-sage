# :advisor — Advisor MCP Server

Stdio MCP server that gives interactive pipeline run analysis. Claude Code connects to it
as a local process and can query the Supabase `jobs`, `transcripts`, and `decision_scores`
tables and call Claude for analysis without leaving the conversation.

## Transport

`stdio` — Claude Code launches the server as a child process and communicates via
stdin/stdout. **Never write to stdout from application code.** All logging goes to stderr
via SLF4J + logback (`logback.xml` uses `<target>System.err</target>`).

## Environment variables (required at runtime)

| Variable               | Purpose                                              |
|------------------------|------------------------------------------------------|
| `SUPABASE_DB_URL`      | Postgres URI (`postgresql://user:pass@host:port/db`) |
| `ANTHROPIC_AUTH_TOKEN` | Anthropic API key                                    |
| `ANTHROPIC_BASE_URL`   | e.g. `https://api.anthropic.com`                     |

Note: `SUPABASE_DB_URL` is a libpq-style URI, not a JDBC URL. `AdvisorDatabase.kt` parses
it with `java.net.URI` and reconstructs a `jdbc:postgresql://` URL for Exposed.

## Tools exposed

| Tool               | Description                                                                          |
|--------------------|--------------------------------------------------------------------------------------|
| `query_runs`       | List recent jobs; filter by ticket key, status, or limit                             |
| `fetch_transcript` | Return raw JSONL transcript for a job                                                |
| `analyze_run`      | Claude-powered analysis using both the transcript and rubric scores (decision_scores)|
| `compare_runs`     | Side-by-side comparison of two jobs (cost, turns, duration, status)                  |
| `explain_failure`  | Claude-powered root cause + proposed fix for a failed job                            |

`analyze_run` returns "No transcript for <id>" for jobs that ran before the `transcripts`
table was created (MS-387). This is expected — not every job has a transcript.

## Package layout

```
advisor/src/main/kotlin/com/mediasage/advisor/
├── AdvisorServer.kt     — main(), server wiring, env var loading
├── AdvisorDatabase.kt   — Exposed DB connection (URI → JDBC URL conversion)
├── AnthropicApi.kt      — API constants (version header, token budgets)
├── ClaudeCall.kt        — HTTP POST to /v1/messages with retry
└── tools/
    ├── QueryRunsTool.kt
    ├── FetchTranscriptTool.kt
    ├── AnalyzeRunTool.kt      — loads DecisionScoresTable + TranscriptsTable
    ├── CompareRunsTool.kt
    └── ExplainFailureTool.kt
```

## Adding a tool

1. Create `tools/MyTool.kt` with an extension function `Server.registerMyTool(...)`.
2. Call it from `AdvisorServer.kt` before `server.createSession(transport)`.
3. Detekt limits: `LongMethod` 30 lines, `ReturnCount` 4. Extract handler body to a
   private `suspend fun` if the lambda grows beyond ~5 lines of logic.

## Packaging

`./gradlew :advisor:shadowJar` → `advisor/build/libs/advisor.jar`

The shadow plugin is applied without a version (`id("com.gradleup.shadow")`) because it is
already on the classpath via `:orchestrator`'s Ktor plugin. Adding a version triggers a
conflict.

## Registering with Claude Code

Build the JAR first, then use the CLI to register it (user scope = available in all projects):

```bash
claude mcp add advisor \
  -s user \
  -e "SUPABASE_DB_URL=$SUPABASE_DB_URL" \
  -e "ANTHROPIC_AUTH_TOKEN=$ANTHROPIC_AUTH_TOKEN" \
  -e "ANTHROPIC_BASE_URL=$ANTHROPIC_BASE_URL" \
  -- java -jar /absolute/path/to/media-sage/advisor/build/libs/advisor.jar
```

The `$VAR` references expand from your shell at registration time — values are stored in
`~/.claude.json`. Never paste literal secrets into the command.

After registering, restart Claude Code. Confirm the server connected with `/mcp`.
