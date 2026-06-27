# MS-383: Advisor MCP Server

## What we built (plain English)

Imagine you're a teacher grading homework. Each piece of homework is a pipeline run where an AI
agent tried to complete a coding ticket. The Advisor is like a grading assistant: you ask it
questions — "how did run XYZ do?", "why did this one fail?", "compare these two runs" — and it
looks up the records and answers you without you having to write any SQL or dig through logs.

It plugs directly into Claude Code so you can interrogate the pipeline while already in a
conversation. Type "use the advisor tool to show me the last 5 runs for MS-383" and it fetches
and formats the answer on the spot.

## How it works

The Advisor is an **MCP server** (Model Context Protocol — Anthropic's open standard for giving
AI assistants tools that run locally). Claude Code launches it as a child process on startup and
talks to it via stdin/stdout in a JSON-RPC protocol.

```
Claude Code  ─── JSON-RPC over stdio ───►  advisor.jar
                                                │
                                            Supabase Postgres
                                            (jobs, transcripts, decision_scores)
                                                │
                                            Anthropic API (analyze / explain tools)
```

### Five tools

| Tool | What it does |
|---|---|
| `query_runs` | List recent jobs with optional ticket-key / status filter |
| `fetch_transcript` | Return the raw JSONL session log for a job |
| `analyze_run` | Ask Claude to analyze turn efficiency using the transcript + rubric scores |
| `compare_runs` | Side-by-side table of cost, turns, duration, status for two jobs |
| `explain_failure` | Ask Claude to read the transcript and diagnose a failure |

`analyze_run` loads both the `transcripts` table and the `decision_scores` table for the job.
The rubric scores (criterion, score/5, rationale, recommendation) are prepended as structured
context above the transcript before calling Claude, so the analysis incorporates what the rubric
already measured rather than re-deriving it from scratch.

### Key implementation lessons

**stdout is sacred in a stdio MCP server.** The JSON-RPC framing travels over stdout, so a
single stray `println()` corrupts the protocol and the connection silently breaks. All logging
goes to stderr via SLF4J + logback with `<target>System.err</target>`. This was the most
surprising constraint of the entire build — MCP effectively takes stdout away from you.

**The MCP SDK types moved.** The official Kotlin SDK 0.9.0 reorganised many types into a
`types.*` subpackage. The quickstart docs hadn't caught up, so the imports the docs showed
(`io.modelcontextprotocol.kotlin.sdk.Tool`) actually resolved to deprecated shims. We discovered
the real package by decompiling the JAR with `javap`. The working imports are
`io.modelcontextprotocol.kotlin.sdk.types.*`.

**The handler is an extension function, not a two-arg lambda.** `addTool { ... }` looks like a
callback, but the lambda is actually `suspend ClientConnection.(CallToolRequest) -> CallToolResult`.
The connection is the implicit `this`; the request is the single explicit parameter. Writing
`{ _, request -> }` produced a compile error about a mismatched argument count.

**`request.arguments` is nullable.** `CallToolRequest.arguments: JsonObject?` — every access
needs `?.get("key")?.jsonPrimitive?.content`. Forgetting the `?.` gives a "only safe calls
allowed on nullable receiver" error.

**`SUPABASE_DB_URL` is a libpq URI, not a JDBC URL.** Exposed's `Database.connect` requires a
`jdbc:postgresql://` URL, but the env var is `postgresql://user:pass@host:port/db`. Passing it
directly throws `Can't resolve dialect`. The fix mirrors the orchestrator's pattern: parse with
`java.net.URI`, extract host/port/path/userInfo, and reconstruct:

```kotlin
val uri = URI(dbUrl)
val (user, password) = uri.userInfo.split(":", limit = 2)
Database.connect(
    url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}?sslmode=require",
    driver = "org.postgresql.Driver",
    user = user,
    password = password,
)
```

**Detekt's `ReturnCount` rule (limit: 4)** forced the compare-tool handler to merge its
six individual argument-validation early returns into three paired checks:

```kotlin
// Instead of six separate ?: return ... statements:
if (idA == null || idB == null) {
    val missing = if (idA == null) "job_id_a" else "job_id_b"
    return CallToolResult(...)
}
```

**Shadow JAR packaging.** The shadow plugin was already on the classpath (via `:orchestrator`'s
Ktor plugin), so applying it with a version number triggered a conflict. The fix is
`id("com.gradleup.shadow")` with no version.

**"No transcript" is expected for older jobs.** The `transcripts` table was added in MS-387.
Any job that ran before that migration has no row there, and `analyze_run` returns
"No transcript for <id>" — this is correct behaviour, not a bug.

### How to register with Claude Code

Build the fat JAR once: `./gradlew :advisor:shadowJar`

Register via the CLI (user scope = available in all projects). Run in your terminal — the `$VAR`
references expand from your shell, so no secrets are hardcoded:

```bash
claude mcp add advisor \
  -s user \
  -e "SUPABASE_DB_URL=$SUPABASE_DB_URL" \
  -e "ANTHROPIC_AUTH_TOKEN=$ANTHROPIC_AUTH_TOKEN" \
  -e "ANTHROPIC_BASE_URL=$ANTHROPIC_BASE_URL" \
  -- java -jar /absolute/path/to/media-sage/advisor/build/libs/advisor.jar
```

This writes to `~/.claude.json`. Restart Claude Code, then confirm with `/mcp` — the advisor
should appear as connected with 5 tools listed.

Note: `mcpServers` is **not** a valid key in `settings.json`. MCP server configuration belongs
in `~/.claude.json`, managed by `claude mcp add`.

## What replaced what

Before this ticket, pipeline analysis meant: open Supabase Studio, write SQL, copy the job ID,
check Cloud Logging, and maybe grep the transcript manually. The Advisor collapses that into a
single natural-language request inside the same conversation you're already in.

The Analyst module (`:feedback`) was retired in MS-447 and MS-449 before this ticket. The Advisor
is lighter and interactive — it doesn't run on a schedule or post autonomously to Jira; it
answers questions on demand.
