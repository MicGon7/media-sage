# MS-299: Supabase Connectivity Check at Agent Startup

## What was built

Added an explicit Supabase DB connectivity check to the agent's startup sequence so a
misconfigured `SUPABASE_DB_URL` fails fast at boot rather than surfacing during a live
webhook handler.

## Problem

`AgentDatabase.init()` was called inside Koin module setup (`buildCloudRunDispatch`).
If the DB was unreachable or the URL was blank, the exception propagated through Koin
with an opaque error message and no explicit process exit, making deployment failures
hard to diagnose.

## Fix

Two changes:

**`AgentDatabase.init()`** — prepends an explicit `exec("SELECT 1")` inside the startup
transaction, before schema migrations run:

```kotlin
transaction {
    exec("SELECT 1")
    migrate()
}
```

This surfaces connectivity failures independently of migration errors.

**`AgentModule.kt`** — extracted DB initialization into `initDatabase(supabaseDbUrl)`:

```kotlin
private fun initDatabase(supabaseDbUrl: String) {
    if (supabaseDbUrl.isBlank()) {
        log.error("SUPABASE_DB_URL is not set — verify environment configuration and restart")
        exitProcess(1)
    }
    try {
        AgentDatabase.init(supabaseDbUrl)
        log.info("Supabase DB connectivity verified")
    } catch (e: Exception) {
        log.error("Failed to connect to Supabase database — verify SUPABASE_DB_URL is set correctly", e)
        exitProcess(1)
    }
}
```

On success, logs `Supabase DB connectivity verified`. On failure, logs a clear,
actionable message and exits with code 1 before the HTTP server opens its port.

## Behavior

| Scenario | Old behavior | New behavior |
|----------|-------------|--------------|
| Blank `SUPABASE_DB_URL` | `error()` thrown in Koin — stack trace logged by Koin | Clear log + `exitProcess(1)` |
| DB unreachable | Exception in Koin — opaque failure | Clear log + `exitProcess(1)` |
| DB reachable | Silent — no confirmation | `Supabase DB connectivity verified` |

## Files changed

- `agent/src/main/kotlin/com/mediasage/agent/db/AgentDatabase.kt` — `SELECT 1` before migrations
- `agent/src/main/kotlin/com/mediasage/agent/di/AgentModule.kt` — `initDatabase()` extracted from `buildCloudRunDispatch()`
