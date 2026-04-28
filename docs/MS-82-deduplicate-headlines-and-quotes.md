# MS-82: Deduplicate headlines returned by the news API

## Problem

TheNewsAPI occasionally returns the same article twice in a single response — same URL, same title, but different UUIDs. Room stored both records, causing duplicate cards on the Home screen.

The encouragement cache also allowed the same `quoteText` to be stored multiple times for the same figure across different articles, inflating the quote count on the Guides detail screen.

## Changes

### Server — headline deduplication

`NewsApiService.getTopHeadlines()` and `searchNews()` now call `.distinctBy { it.url }` on the API response before returning. The client never sees duplicate articles regardless of what the upstream API sends.

### Shared — quote uniqueness constraint

`EncouragementEntity` gained a unique index on `(figureName, quoteText)`:

```kotlin
@Entity(
    tableName = "encouragements",
    indices = [Index(value = ["figureName", "quoteText"], unique = true)]
)
```

`EncouragementDao.insert` was changed from `OnConflictStrategy.REPLACE` to `OnConflictStrategy.IGNORE`. When the same quote text would be stored for the same figure (matched to a different article), Room silently drops the duplicate. The encouragement result is still returned to the user for the current session — it simply isn't persisted again.

### Database version bump

`MediaSageDatabase` version bumped from 5 → 6. Both platforms use `fallbackToDestructiveMigration`, so the schema is recreated on upgrade, clearing any pre-existing duplicate rows.

### Quote count

The Guides detail screen already used `state.quotes.size` derived from `EncouragementDao.getByFigureName()`. With the unique constraint in place, this count now reflects only distinct quote texts per figure — no additional UI changes were needed.

## Tests

- `NewsApiServiceTest` — two new tests: `getTopHeadlinesDeduplicatesByUrl` and `searchNewsDeduplicatesByUrl`. Both verify that articles with duplicate URLs are collapsed to a single entry before being returned.
- `EncouragementRepositoryTest` (new file) — four tests using a fake `EncouragementDao` and fake `MediaSageApi`:
  - `returnsCachedEncouragementWhenArticleUrlHit` — cache hit returns stored data without calling the API
  - `callsApiAndSavesWhenNoCacheHit` — cache miss calls the API and persists the result
  - `doesNotSaveDuplicateQuoteTextForSameFigure` — two articles returning the same `(figureName, quoteText)` result in only one insert
  - `doesNotCallApiWhenArticleUrlIsNull` — null URL skips persistence entirely

## Pattern

Deduplication is enforced at two layers:
- **Server boundary**: `distinctBy { it.url }` before the response leaves the server
- **Database boundary**: unique index + `IGNORE` strategy at insert time

This is the standard defense-in-depth pattern for external API data: sanitize at the source, enforce invariants at the database.
