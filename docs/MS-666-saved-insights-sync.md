# MS-666: Sync Saved Insights (Bookmarked Encouragements) to Supabase Per User

## What Changed
Bookmarking an encouragement now syncs the full saved insight — figure, quote, scripture,
explanation, summary — to Supabase per signed-in user, client-side, following the MS-51/MS-664
outbox pattern. Unlike either prior sync ticket, `EncouragementEntity` also holds shared cache
content that has nothing to do with any one account, so three operations had to depart from the
MS-51/MS-664 shape rather than copy it directly.

## Why This Ticket Isn't a Direct Copy of MS-51/MS-664
`day_assignment` and `daily_reflection` are pure user-owned rows — the synced entity *is* the
user's data. `EncouragementEntity` isn't: most of its columns (`quoteText`, `scriptureText`,
`explanation`, etc.) are cache content shared by anyone who matches the same `articleUrl`. Only
`bookmarked` (and now the sync bookkeeping around it) is actually user-scoped. That changes three
operations:

1. **Account-switch guard** — MS-51/MS-664 wipe the whole table (`clearAll()`) when a different
   account previously synced on this device. Here that would delete shared cache content that has
   nothing to do with bookmarks. Instead, `resetBookmarkStateForAccountSwitch()` resets only
   `bookmarked`/`synced`/`pendingDelete` on currently-bookmarked rows, leaving cache content intact.
2. **Tombstone delete** — MS-51 purges the row once a delete is confirmed remotely. Here, unbookmarking
   must never delete the row — the user might still be viewing that match in the "All" list.
   `clearBookmarkState()` resets bookmark/sync flags back to neutral without touching the row's
   content.
3. **Pending-sync query** — MS-51/MS-664 filter `WHERE synced = 0` alone, since every row in those
   tables is user data. Here most rows are never bookmarked, so `getPendingSync()` is
   `WHERE pendingDelete = 1 OR (bookmarked = 1 AND synced = 0)` — otherwise every freshly-cached,
   never-bookmarked encouragement would look like a pending push.

## Client Changes

### Room Schema — Version 27 → 28
- `encouragements` gains `synced: Boolean = true` (defaults **true**, unlike MS-51/MS-664's `false`
  default — a non-bookmarked cache row has nothing to push) and `pendingDelete: Boolean = false`.
- `sync_meta` gains `lastSavedInsightSyncUserId: String?`.
- `MIGRATION_27_28` — plain `ALTER TABLE ADD COLUMN`s, registered in both
  `DatabaseBuilder.android.kt` and `DatabaseBuilder.ios.kt`.

### `EncouragementDao.toggleBookmark` — One Statement, Both Directions
The existing toggle query now derives `synced`/`pendingDelete` from the pre-update `bookmarked`
value in the same `UPDATE` (standard SQL semantics: right-hand-side expressions read the old row).
Bookmarking (`false -> true`) marks the row unsynced, pending a full-content push. Unbookmarking
(`true -> false`) sets `pendingDelete`, pending a remote delete. The repository reads the row back
once after the toggle to decide which push path to take — no second DAO call needed to know the
direction.

### `SavedInsightRemoteDataSource` — Mirrors `DayAssignmentRemoteDataSource`
Same shape: a small interface (`push`/`delete`/`fetchAll`, needs delete unlike
`DailyReflectionRemoteDataSource`) wrapping Postgrest calls, with
`PostgrestSavedInsightRemoteDataSource` as the real implementation. `SavedInsightRow` carries the
full snapshot (including `connectionThemes` as `List<String>`, matching `sources` on
`DailyReflectionRow`, translated to/from `EncouragementEntity`'s comma-joined string at the
repository boundary). Registered in Koin only when Supabase credentials are configured.

### `EncouragementRepositoryImpl.resolve(userId)`
Three phases when signed in, mirroring `DayAssignmentRepositoryImpl.syncWithRemote`:
1. **Account-switch guard**: bookmark-scoped reset (see above), not a full wipe.
2. **Push pending**: everything with `pendingDelete = true` or `(bookmarked = true, synced = false)`
   gets pushed or deleted; success marks it synced / clears bookmark state, failure leaves it for the
   next pass.
3. **Pull + reconcile**: fetch all remote saved insights for the user. A local row that's unsynced or
   pending-delete wins over the pull (retried on the next push pass) — same rule as MS-51. A remote
   row with no local counterpart is a straight upsert with `bookmarked = true, synced = true` — this
   is the actual point of the ticket: full content appears on a device that never cached that match.
   Any locally-bookmarked+synced row absent from the pull (even an empty pull) gets `bookmarked =
   false` — unbookmarked elsewhere — never deleted.

`toggleBookmark()` still pushes immediately and inline, mirroring `assign()`/`clear()`.

## Result
Manually validated on two Android emulators signed into the same account, covering all four ACs:
bookmarking on one device showed the full saved insight on the second; a cleared/reinstalled app
pulled existing saved insights with full content; unbookmarking on one device removed it on the
other; and an offline bookmark completed locally and synced once connectivity returned. No issues
found.

## Supabase Side
Added `supabase/migrations/0003_saved_insights_sync.sql` — table + `auth.uid() = user_id` RLS
policy, keyed `(user_id, article_url)`. **This has to be run once, manually, via the Supabase SQL
editor**, same as MS-51/MS-664's migrations.

## What We Didn't Do (and Why)
- **Connectivity-triggered retry**: pending pushes/deletes still only drain on the next app launch
  or sign-in, same limitation MS-51/MS-664 already accepted. A real connectivity-triggered background
  drain (WorkManager/BGTaskScheduler behind a KMP `expect`/`actual` seam, shared across all three sync
  repositories) was scoped out during design review as its own follow-up — see MS-668.
- **A dedicated `SavedInsightEntity` table**: considered instead of extending `EncouragementEntity`
  in place, but it would duplicate every content column `EncouragementEntity` already has (a saved
  insight still needs the full snapshot to render on a device that never generated that match) while
  adding a second source of truth to keep consistent. Extending in place means pulled content lands
  directly where `observeAll`/`observeBookmarked`/`observeByFigureId` already read from.
