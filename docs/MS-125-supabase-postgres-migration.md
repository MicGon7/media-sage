# MS-125 — Migrate Server DB to Supabase Postgres

## What we built

Replaced the ephemeral Railway SQLite database with Supabase Postgres as the persistent server-side store. The server now connects to Supabase Postgres when `SUPABASE_DB_URL` is set, and falls back to SQLite for local dev without it. Seeding moved from a programmatic startup routine (`FigureSeeder`) to SQL scripts run once in the Supabase SQL Editor.

## Why this was necessary

Railway's SQLite filesystem is wiped on every deploy. `FigureSeeder` re-seeded figures on startup, but quotes were never re-seeded — leaving `/api/analysis/encourage` broken after every deploy (empty quote pool → Claude returns null for `selectedQuoteId` → 400). Supabase Postgres is persistent, survives deploys, and is already in use for Storage.

## How it works

### DB connection

`ServerDatabase.init()` accepts an optional `postgresUrl` parameter. When set, it parses the Supabase connection string (`postgresql://user:password@host:port/dbname`) and connects via `org.postgresql.Driver` with `sslmode=require`. When null, it falls back to SQLite.

`application.conf` reads `SUPABASE_DB_URL` from the environment:
```
app.supabase.dbUrl = ${?SUPABASE_DB_URL}
```

`Application.initDatabase()` checks which URL is available and routes accordingly. No other startup code changed — Exposed's `SchemaUtils.createMissingTablesAndColumns` creates the tables on first boot regardless of which DB is used.

### Seeding

Two SQL seed files under `server/src/main/resources/`, each run once in the Supabase SQL Editor:

- **`seed_figures.sql`** — 100 figures with names, roles, bios, portrait URLs, and enabled status. Generated from the local SQLite DB using a custom query that produces Postgres-compatible SQL (named columns, `true`/`false` booleans). Ends with a sequence reset.
- **`seed_quotes.sql`** — ~1,000 verified quotes across all 100 figures. Boolean `verified` values use `true`/`false`. Ends with a sequence reset.

Both files end with:
```sql
SELECT setval('<table>_id_seq', (SELECT MAX(id) FROM <table>));
```

This is required after inserting explicit IDs into Postgres `SERIAL` columns — without it, future auto-generated IDs conflict with seeded ones.

### Removed: FigureSeeder and WikimediaService

`FigureSeeder` fetched Wikipedia bios and upserted figures on every server startup. With a persistent Postgres DB, startup seeding is unnecessary. `WikimediaService` was no longer injected anywhere (portrait URLs moved to Supabase Storage in MS-122). Both were deleted along with `WikimediaServiceTest`. Future bios will be generated via Claude.

### Defensive fix: nullable selectedQuoteId

`SelectionResult.selectedQuoteId` changed from `Long` to `Long?`. When Claude returns null, deserialization no longer throws a 400 — `resolveSelection()` treats null as a miss and goes straight to the retry path.

## Key gotcha: Postgres boolean columns

SQLite stores booleans as `INTEGER` (0/1). Postgres `BOOLEAN` columns reject integer literals — they require `true`/`false`. The raw SQLite dump produces `1`/`0` which fails in Postgres. Fix: generate seed SQL with a custom SQLite query that emits `true`/`false`, or run a `sed` pass on an existing dump.

## Setup after first deploy

After merging and Railway deploying (Exposed creates the empty tables on startup):

1. Run `seed_figures.sql` in Supabase SQL Editor
2. Run `seed_quotes.sql` in Supabase SQL Editor

## Local dev

Without `SUPABASE_DB_URL` set, the server uses SQLite as before. To seed locally:
```bash
sqlite3 $DB_PATH < server/src/main/resources/seed_figures.sql
sqlite3 $DB_PATH < server/src/main/resources/seed_quotes.sql
```
