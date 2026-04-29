# MS-95: History — Persist Headline Image and Source in Encouragement Cache

## Problem

Two runtime bugs in the history feature discovered during manual testing:

1. **Placeholder images** — History cards showed a placeholder instead of the article thumbnail.
2. **Cannot navigate to detail** — Tapping a history item did nothing because `headlineId` was null.

## Root Cause

`HistoryViewModel` was resolving both the image and the headline ID by querying `HeadlineDao` at display time:

```kotlin
val headline = headlineDao.getByUrl(entity.articleUrl)
HistoryItem(headlineId = headline?.id, imageUrl = headline?.imageUrl)
```

But `HeadlineRepositoryImpl.refreshHeadlines()` calls `deleteAll()` before every refresh. After any app restart or pull-to-refresh, the old `HeadlineEntity` rows are gone. The DAO returned null, leaving both fields empty.

A secondary issue: `headlineId` is an auto-increment SQLite ID — a terrible navigation key, since it changes whenever rows are deleted and reinserted. Even within a single session it could become invalid.

## Fix

### EncouragementEntity is the history record

The `EncouragementEntity` is already the source of truth for "articles the user has viewed." It needed two more fields stored at cache time:

- `headlineImageUrl: String?` — the news article's thumbnail
- `headlineSource: String` — the news source name

These are populated in `EncouragementRepositoryImpl.getEncouragement()`, which is called by `HeadlineDetailViewModel` when the user first views an article — at that point the headline is guaranteed to be in the DB.

### Navigation by articleUrl

`headlineId` was removed from navigation entirely. `Route.HeadlineDetail` now carries `articleUrl: String` — a stable key that never changes. `HeadlineDetailViewModel` looks up the current `HeadlineEntity` by URL via `getHeadlineByUrl()`. If the headline has been refreshed away, it falls back to the stored `headlineTitle`, `headlineSource`, and `headlineImageUrl` on the encouragement. The detail screen always renders.

This also incidentally fixes a latent bug on the Home screen: pulling to refresh assigns new auto-increment IDs, so any `headlineId` resident in the navigation back stack was stale.

### HistoryViewModel

`HeadlineDao` dependency removed entirely. Image and source come straight from the entity:

```kotlin
HistoryItem(
    headlineImageUrl = entity.headlineImageUrl,
    ...
)
```

### DB migration

`MediaSageDatabase` bumped from version 7 to 8. Both platforms use `fallbackToDestructiveMigration`, so no manual migration script is needed.

## Files Changed

| File | Change |
|------|--------|
| `EncouragementEntity` | Added `headlineImageUrl`, `headlineSource` |
| `Encouragement` (domain) | Added `headlineTitle`, `headlineSource`, `headlineImageUrl` for fallback rendering |
| `EntityMappers` | Updated `toEntity()` / `toDomain()` for new fields |
| `EncouragementRepository` | Added `headlineSource`, `headlineImageUrl` params to `getEncouragement()` |
| `EncouragementRepositoryImpl` | Stores new fields at cache time |
| `HeadlineRepository` | Added `getHeadlineByUrl(url)` |
| `HeadlineRepositoryImpl` | Implemented `getHeadlineByUrl` via `HeadlineDao.getByUrl` |
| `HeadlineDetailViewModel` | Now takes `articleUrl`, lookups by URL, falls back to encouragement |
| `Route.HeadlineDetail` | Changed from `headlineId: Long` to `articleUrl: String` |
| `MediaSageAppState` | `navigateToHeadlineDetail` now accepts `String` (URL) |
| `HomeContract` / `HomeScreen` / `HomeViewModel` | Navigation passes `articleUrl` instead of `id` |
| `HistoryContract` | `HistoryItem` drops `headlineId`, renames `imageUrl` to `headlineImageUrl` |
| `HistoryViewModel` | Removed `HeadlineDao` dependency; reads image from entity |
| `HistoryScreen` | Updated signature and click handler to use `articleUrl` |
| `MediaSageDatabase` | Version 7 → 8 |

## Key Pattern

> The `EncouragementEntity` is a self-contained history record. Store all display data at cache time. Never re-query ephemeral tables (headlines) at display time.
