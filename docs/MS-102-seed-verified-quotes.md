# MS-102: Generate and Seed Verified Quotes for All 100 Figures

## What was built

A SQL seed script (`server/src/main/resources/seed_quotes.sql`) containing 707 verbatim historical quotes across all 100 figures. The script is idempotent (`DELETE FROM quotes` at the top) and applied directly to the SQLite database. `DB_PATH` env var plumbing was added so the server always uses an explicit, absolute DB path rather than creating stray files.

## Key decisions

### SQL seed script over programmatic seeder
The original `QuoteSeeder.kt` called Claude at startup to generate quotes dynamically. It was deleted in favour of a static SQL file because:
- Reviewable in PRs — every quote and source is visible
- Reproducible — re-run anytime the DB is wiped: `sqlite3 $DB_PATH < seed_quotes.sql`
- No runtime token cost
- Idempotent with `DELETE FROM quotes;` at the top

Programmatic seeders are appropriate for dynamic/runtime data. Fixed historical quotes are not that.

### Verbatim only, no fabrication
Every quote in the seed file is verbatim (or a well-known English translation for non-English originals) from a specific, traceable source — book title + year, letter name, sermon title. Rows were skipped rather than invented when a verified quote could not be confirmed. This produced 707 rows rather than 1,000, which is intentional: a shorter file with trustworthy quotes beats 1,000 rows with invented ones.

### Write file before applying to DB
A previous session lost ~150 quotes by piping SQL directly to `sqlite3` via a heredoc. When DB Browser held the journal open and then closed, SQLite rolled back all uncommitted changes. The rule now: always write to the repo file first, then apply with `sqlite3 $DB_PATH < seed_quotes.sql`.

### DB_PATH env var
`ServerDatabase.init()` previously defaulted to `"mediasage-server.db"` relative to whatever the working directory was at launch time, creating stray DB files. Now:
- `DB_PATH` env var is set in `~/.zshrc` with the absolute path
- `application.conf` reads `${?DB_PATH}` and passes it to `ServerDatabase.init(dbPath)`
- Server calls `error()` fast if `DB_PATH` is not set
- `ApplicationTest` uses `MapApplicationConfig("app.db.path" to ":memory:")` for isolation

### Exposed `source` column naming conflict
`source` is a reserved property name on Exposed's `ColumnSet` supertype. The Kotlin property remains `sourceText` while the DB column name is `"source"`. SQL seed uses `source` directly.

### verified column
All seed quotes have `verified = 1`. This field distinguishes curated historical quotes from any future AI-generated quotes.

## Quote coverage

707 quotes across 100 figures. Figures with a full 10 include: Luther, Calvin, Bonhoeffer, Spurgeon, Wesley, Edwards, Thomas à Kempis, Augustine, MLK Jr., Corrie ten Boom, Pascal, C.S. Lewis, Chesterton, Mother Teresa, Andrew Murray. Figures with fewer (3–6) are those where fewer verified verbatim sources were available: Mendel (3), Galileo (4), Polycarp (4), Nate Saint (4), Gladys Aylward (4).

## Files changed

- `server/src/main/resources/seed_quotes.sql` — 707 verbatim quotes, idempotent seed script
- `server/src/main/kotlin/com/mediasage/server/Application.kt` — reads `app.db.path` from config, passes to `ServerDatabase.init()`
- `server/src/main/resources/application.conf` — `app.db.path = ${?DB_PATH}`
- `server/src/test/kotlin/com/mediasage/server/ApplicationTest.kt` — `MapApplicationConfig("app.db.path" to ":memory:")`
- `shared/.../QuoteEntity.kt` — `verified: Boolean = false`
- `shared/.../Quote.kt` — `verified: Boolean = false`
- `shared/.../EntityMappers.kt` — updated quote mappers for `verified`
- `shared/.../MediaSageDatabase.kt` — schema version bump
- `shared/schemas/` — Room schema export
