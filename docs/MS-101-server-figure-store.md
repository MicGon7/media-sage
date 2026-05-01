# MS-101: Server-Side Figure Store

## What was built

A server-owned SQLite figure store seeded with 100 curated historical Christian figures across 6 categories. The server exposes `GET /api/figures` returning all enabled figures with their Wikipedia bios.

## Why server-owned

Figures are central to the encouragement flow — Claude needs access to figure metadata and themes when selecting who speaks to a headline (MS-103). Owning figures on the server means that data is available at match time without requiring the client to upload it. The client syncs a local copy via MS-108.

## Schema (FigureTable)

| Column | Type | Notes |
|---|---|---|
| id | LONG | Auto-increment primary key |
| name | VARCHAR(255) | Unique index |
| category | VARCHAR(64) | Internal filter only — never surfaced as a UI label |
| century | VARCHAR(32) | e.g. "19th" |
| role | VARCHAR(255) | Display label e.g. "Pastor & Author" |
| lifespan | VARCHAR(64) | e.g. "1828-1917" |
| bio | TEXT | Wikipedia summary extract |
| themes | TEXT | Reserved for MS-102 (verified quotes + themes) |
| portraitUrl | VARCHAR(512) | Nullable — reserved for MS-104 (oil painting portraits) |
| isEnabled | BOOL | Server admin toggle; false = hidden from client |

## Seeding strategy

`FigureSeeder` runs on server startup and is idempotent:
- Skips entirely if all 100 figures exist with non-empty bios
- Re-fetches only figures with empty bios on restart (safe retry)

Bios are fetched from the **Wikipedia REST API** (`/api/rest_v1/page/summary/{title}`):
- Requires `User-Agent` header — missing this causes 429 rate limiting
- 500ms delay between requests (verified stable; the old action API needed 1500ms+)
- Disambiguation pages are detected via `"may refer to:"` in the extract or `type == "disambiguation"` — stored as empty string so retry logic picks them up on next restart

## Disambiguation overrides

Some figure names resolve to Wikipedia disambiguation pages. These are fixed via `wikipediaTitle` on `FigureSeed`:

| Figure | Wikipedia title used |
|---|---|
| Jonathan Edwards | Jonathan Edwards (theologian) |
| John Owen | John Owen (theologian) |
| William Carey | William Carey (missionary) |
| John Perkins | John M. Perkins |
| Andrew Murray | Andrew Murray (minister) |

## Categories (internal only)

Categories are used for internal grouping and future filtering. They are intentionally **not surfaced as UI labels** — the `role` field is what the UI displays. This avoids theological pigeonholing (e.g. labelling Watchman Nee as "mystic" without context).

| Key | Display name |
|---|---|
| theologian | Theologians & Reformers |
| mystic | Mystics & Contemplatives |
| church_father | Church Fathers |
| social_justice | Social Justice & Public Faith |
| intellectual | Scientists & Intellectuals |
| missionary | Missionaries & Servants |

## Endpoint

`GET /api/figures` — returns all figures where `isEnabled = true` as a JSON array of `FigureDto`.

## Dependencies added

- `org.jetbrains.exposed:exposed-core:0.61.0`
- `org.jetbrains.exposed:exposed-jdbc:0.61.0`
- `org.xerial:sqlite-jdbc:3.47.1.0`
