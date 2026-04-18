# MS-17: Testing Infrastructure

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-17

## What was built

Testing infrastructure across all three modules:

- **Server** (`ApplicationTest.kt`): Integration tests using `ktor-server-test-host` and `testApplication {}` DSL. Tests health endpoint (200 + body) and unknown route (404).
- **Shared** (`HttpClientTest.kt`): Unit tests using `ktor-client-mock` and `MockEngine`. Tests mock HTTP responses for success and error cases. Uses `runTest` for coroutine testing.
- **composeApp** (`ComposeAppCommonTest.kt`): Updated placeholder test to verify `Greeting` and `Platform` produce non-empty values.
- **CLAUDE.md**: Updated testing conventions section with patterns, naming, and rules.

## Key decisions & why

- **`testApplication {}` DSL over manual server setup**: Ktor's test host runs the full plugin pipeline in-memory without binding to a port. Faster and more reliable than starting a real server.
- **`MockEngine` for shared module**: The shared module's HTTP tests shouldn't hit real endpoints. MockEngine lets you define canned responses and assert against the request/response cycle.
- **`runTest` over `runBlocking`**: `runTest` from kotlinx-coroutines-test skips delays and provides better error reporting for suspending test functions.
- **Meaningful composeApp tests**: Replaced the trivial `1 + 2 = 3` test with actual assertions against app code (`Greeting`, `Platform`).

## Concepts learned

- **`testApplication {}`**: Ktor's testing DSL. Creates an in-memory server with your module installed. Returns a `client` you can use to make HTTP requests. No port binding, no network — pure in-process testing.
- **`MockEngine`**: Ktor Client's test engine. You provide a lambda that receives each request and returns a mock response. Useful for testing networking code without real HTTP calls.
- **Test source sets in KMP**: `commonTest` runs on all platforms. Server tests go in standard `src/test/kotlin/`. Each target can also have platform-specific tests (`androidTest`, `iosTest`).

## Gotchas

- Server test directory must be `src/test/kotlin/` (standard JVM convention), not `src/commonTest/` (that's for KMP modules).
- `testApplication` automatically installs your module — don't install plugins twice or you'll get "plugin already installed" errors.
