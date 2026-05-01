# MS-102: Generate and Seed Verified Quotes for All 100 Figures

## What was built

A server-side `QuoteSeeder` that calls Claude once per figure to generate 20 historically plausible quotes, stored in the server's `quotes` table. The encourage flow was also updated with server-side figure diversity tracking.

## Key decisions

### 20 quotes per figure
Enough pool depth for theme-based filtering to surface varied candidates across repeated encourage calls for the same headline topics.

### Exposed `source` column naming conflict
`source` is a reserved property name on Exposed's `ColumnSet` supertype. Renamed the Kotlin property to `sourceText` while keeping the DB column name as `"source"`.

### Server-side recentFigures LinkedHashSet
Replaced client-passed `recentFigures` list with an in-memory `LinkedHashMap` (used as a capped set, max 10) on `ClaudeApiService`. Insertion-ordered — the oldest figure drops off when the 11th is added, making it eligible again. This is a sliding window, not a permanent exclusion.

Removing `recentFigures` from the client request DTO simplified the shared module and moved the concern to the correct layer.

### callClaude() maxTokens parameter
Quote generation needs 4096 tokens (20 quotes per call); encourage needs only 1024. Added an optional `maxTokens` parameter with a 1024 default rather than a separate method.

### QuoteSeeder idempotency
Checks by `figureId + text` before inserting. Re-running the seeder on a populated database skips figures that already have 20+ quotes and deduplicates any partial runs.

### FigureRoutesTest isolation fix
The test was using `@BeforeTest` with `INSERT` statements on a shared Exposed connection. Added `SchemaUtils.drop` + `SchemaUtils.create` in `@BeforeTest` and `SchemaUtils.drop` in `@AfterTest` to ensure a clean state per test run.

## Smoke test results (2026-05-01)

Verified with two consecutive POST `/api/analysis/encourage` calls:
- Call 1: returned Blaise Pascal
- Call 2: returned Irenaeus (Pascal excluded by server-side LinkedHashSet)

Server-side figure diversity confirmed working without any client changes.

## Files changed

- `server/db/QuoteTable.kt` — Exposed table for `quotes`
- `server/db/QuoteSeeder.kt` — batch seeder, idempotent, 20 quotes/figure
- `server/service/ClaudeApiService.kt` — `generateQuotesForFigure()`, optional `maxTokens`, server-side recentFigures LinkedHashSet
- `server/routes/AnalysisRoutes.kt` — removed `recentFigures` from `EncourageRequest`
- `server/db/ServerDatabase.kt` — registered `QuoteTable`
- `server/Application.kt` — calls `QuoteSeeder.seed()` after `FigureSeeder.seed()`
- `shared/.../QuoteEntity.kt` — added `verified: Boolean = false`
- `shared/.../Quote.kt` — added `verified: Boolean = false`
- `shared/.../EntityMappers.kt` — updated quote mappers for `verified`
- `shared/.../MediaSageDatabase.kt` — version 11 → 12
- `shared/.../ApiDtos.kt` — removed `recentFigures` from `EncourageRequestDto`
- `shared/.../EncouragementRepositoryImpl.kt` — removed recentFigures lookup
- `shared/schemas/.../12.json` — Room schema export
