# MS-127: Timestamp-Based Delta Sync for Figures

## What Changed
Replaced the delete-all-and-reinsert sync pattern with a timestamp-based delta sync. On every app launch, the client now sends `?since=<epochMillis>` to `/api/figures` and receives only figures updated after that timestamp. Every 7 days, a full sync runs automatically to self-heal any drift (deletions, corruption, etc.).

## Why It Matters
The previous `syncFigures()` deleted all 100 Room figures and re-inserted them on every launch — wasteful on battery and data. More importantly, a count-based approach (considered and rejected) only detects additions. Timestamp-based delta sync catches additions, edits to bio/portrait/role, and self-heals deletions via the 7-day full sync.

## Server Changes

### `FigureTable.kt`
Added `updated_at BIGINT DEFAULT 0` column. `SchemaUtils.createMissingTablesAndColumns` auto-adds it to Supabase Postgres on next deploy — existing rows get 0 and need a one-time backfill (see below).

### `FigureRepository.kt`
`getAllEnabled(since: Long? = null)` — when `since` is provided, filters `WHERE updated_at > since`. The condition is built inline in the Exposed `where {}` lambda using the imported `and` infix function (`org.jetbrains.exposed.sql.and`).

### `FigureRoutes.kt`
Reads `?since=` query param via `call.request.queryParameters["since"]?.toLongOrNull()` and passes it to the repository.

## Client Changes

### Room Schema — Version 12 → 13
New `SyncMetaEntity` table (`sync_meta`) with a single row storing `lastFigureSyncAt: Long?`. No changes to the `figures` table. Migration `MIGRATION_12_13` creates the table; `fallbackToDestructiveMigration` is kept as a safety net but the explicit migration runs first on upgrades.

### KMP Migration API
Room 2.7.1 KMP uses `androidx.sqlite.SQLiteConnection` (not `SupportSQLiteDatabase`). The `execSQL` extension comes from `androidx.sqlite:sqlite-ktx` (already a transitive dependency). Migration is defined in `commonMain` and wired into both Android and iOS `getDatabaseBuilder()`.

### `FigureRepositoryImpl.kt` — Delta Sync Logic
```
syncFigures():
  1. Read lastFigureSyncAt from sync_meta
  2. Record syncStartedAt = currentTimeMillis()
  3. If first sync (null) or ≥ 7 days: isFullSync = true
  4. Call getFigures(since = if(isFullSync) null else lastSyncAt)
  5. Full sync: deleteAll() + insertAll(). Delta: insertAll() only if non-empty (REPLACE = upsert)
  6. Save syncStartedAt to sync_meta
```

Key: use `syncStartedAt` (time the request was made) as the stored timestamp, not `max(updatedAt)` from the response. This prevents edge cases where a figure updated between request and response is missed on the next delta.

### `currentTimeMillis()` in KMP
`System.currentTimeMillis()` is not available in KMP common. The project already has an `internal expect fun currentTimeMillis(): Long` in `com.mediasage.data.repository` with Android/iOS actuals. Used that directly — same package, no import needed.

## Supabase Backfill (one-time)
After deploying, existing figures have `updated_at = 0`. Run in Supabase SQL Editor:
```sql
UPDATE figures SET updated_at = EXTRACT(EPOCH FROM NOW()) * 1000 WHERE updated_at = 0;
```
This ensures a full sync (null `since`) correctly returns all figures for fresh installs, and subsequent delta syncs send a timestamp after this backfill so they correctly receive nothing when nothing has changed.

## Sync Behavior Summary
| Scenario | Behavior |
|---|---|
| Fresh install | `since=null` → all enabled figures → full replace |
| < 7 days since last sync, nothing changed | `since=T` → empty list → no Room write |
| < 7 days, figure edited | `since=T` → delta list → REPLACE upsert |
| ≥ 7 days | `since=null` → full replace → self-heals deletions |

## What We Didn't Do (and Why)
- **Tombstone/soft-delete pattern**: Not implemented. Deletions are handled by the 7-day full sync. For 100 stable historical figures managed by a single admin, the complexity of `deletedIds` or a `deleted_at` column isn't justified. Revisit if the figure dataset grows to user-contributed content.
- **ETag / 304 Not Modified**: Real optimization for high-traffic APIs. Skipped — the delta payload for 100 figures is tiny and the `since` timestamp already prevents unnecessary Room writes.
