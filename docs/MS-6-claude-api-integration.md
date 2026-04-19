# MS-6: Claude API Integration Service

**Epic:** MS-1 (Server API Layer)
**Date completed:** 2026-04-18

## What was built

Server-side Claude API integration for matching news headlines with encouraging quotes.

### Service layer (`server/service/`)
- **ClaudeApiDtos.kt** — Request/response DTOs matching Claude's Messages API (`ClaudeRequest`, `ClaudeResponse`, `ContentBlock`, `Usage`, `ClaudeErrorResponse`)
- **ClaudeApiService.kt** — HTTP client service with `matchQuoteToHeadline()` public API, system prompt, JSON response parsing with markdown fence stripping

### Route (`server/routes/AnalysisRoutes.kt`)
- `POST /api/analysis/match` — receives headline + candidate quotes, returns match result

### DI (`server/di/ServerModule.kt`)
- Koin module providing `HttpClient(OkHttp)` and `ClaudeApiService` with API key from config

### Application wiring
- Koin installed in `Application.module()` reading API key from `application.conf` → `CLAUDE_API_KEY` env var

## Key decisions & why

- **Server calls Claude, not the mobile app**: API key stays on the server. Mobile app never sees it. Standard backend-for-frontend pattern.
- **`@SerialName` for snake_case mapping**: Claude's API uses `max_tokens`, `stop_reason`, etc. `@SerialName` bridges to Kotlin's camelCase without a global naming strategy.
- **Reusable `responseJson` instance**: Extracted to companion object to avoid creating a new `Json` instance per parse call (IDE caught this as a performance warning).
- **`extractJson()` for markdown fences**: Claude sometimes wraps JSON in ` ```json ``` ` blocks. Regex strips them before parsing. Defensive but necessary.
- **System prompt as a constant**: Defined as a file-level `val` in `ClaudeApiService.kt`. Easy to find, easy to iterate on. Will move to a config file if we need A/B testing (Phase 4).
- **Sonnet model**: `claude-sonnet-4-6` for cost-effective matching. Can upgrade to Opus for higher quality if needed.

## Concepts learned

- **Claude Messages API**: POST to `/v1/messages` with `x-api-key` and `anthropic-version` headers. System prompt separate from messages. Response contains content blocks (usually one text block).
- **Ktor client in server module**: Same Ktor client library as the shared module, but using OkHttp engine (JVM-only). ContentNegotiation auto-serializes request bodies and deserializes responses.
- **Koin in Ktor**: `install(Koin) { modules(...) }` in the application module. Routes use `inject<T>()` for lazy injection. Config values read from `environment.config` and passed to Koin module factory.
- **System prompt design**: Persona + guidelines + output format. The more specific the JSON schema in the prompt, the more reliable the structured output.
- **MockEngine for testing**: Same pattern as shared module — provide canned responses, assert against parsed results. No real HTTP calls in tests.

## Gotchas

- **`install()` unresolved**: Needed wildcard import `import io.ktor.server.application.*` instead of specific import for `install()` to resolve in the Application context.
- **MockEngine not found**: Had to add `ktor-client-mock` and `kotlinx-coroutines-test` to server's test dependencies — they were only in the shared module.
- **API key in env var**: `CLAUDE_API_KEY` must be in `~/.zshrc` for the server to read it. The env var doesn't carry over between different shell processes (same issue as SSH keys in Claude Code).
- **Personal vs company API account**: Use personal Anthropic account (micgon7@gmail.com) for the API — company domain blocks new org creation.
