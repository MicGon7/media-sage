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
                                            Supabase Postgres (jobs, transcripts)
                                                │
                                            Anthropic API (analyze / explain tools)
```

### Five tools

| Tool | What it does |
|---|---|
| `query_runs` | List recent jobs with optional ticket-key / status filter |
| `fetch_transcript` | Return the raw JSONL session log for a job |
| `analyze_run` | Ask Claude to count agentic turns and flag wasted cycles |
| `compare_runs` | Side-by-side table of cost, turns, duration, status for two jobs |
| `explain_failure` | Ask Claude to read the transcript and diagnose a failure |

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

### How to register it with Claude Code

Build the fat JAR once: `./gradlew :advisor:shadowJar`

Add to `.claude/settings.json`:

```json
{
  "mcpServers": {
    "advisor": {
      "command": "java",
      "args": ["-jar", "/path/to/media-sage/advisor/build/libs/advisor.jar"],
      "env": {
        "SUPABASE_DB_URL": "${SUPABASE_DB_URL}",
        "ANTHROPIC_AUTH_TOKEN": "${ANTHROPIC_AUTH_TOKEN}",
        "ANTHROPIC_BASE_URL": "${ANTHROPIC_BASE_URL}"
      }
    }
  }
}
```

The `${VAR}` syntax pulls from your shell environment — no secrets go in the file itself.

## What replaced what

Before this ticket, pipeline analysis meant: open Supabase Studio, write SQL, copy the job ID,
check Cloud Logging, and maybe grep the transcript manually. The Advisor collapses that into a
single natural-language request inside the same conversation you're already in.

The Analyst module (`:feedback`) was retired in MS-447 and MS-449 before this ticket. The Advisor
is lighter and interactive — it doesn't run on a schedule or post autonomously to Jira; it
answers questions on demand.
