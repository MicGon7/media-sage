# MS-664: Sync Daily Reflections (Past Briefings) to Supabase Per User

## What Changed
Daily reflections (`daily_reflection` — the generated scripture/insight/implication/inspiration
content behind each day's briefing) now sync to Supabase per signed-in user, client-side, following
the MS-51 `day_assignment` template almost exactly — but simplified for the append-only case: a
reflection is generated once and never edited, so reconciliation is a plain union by key instead of
MS-51's per-row synced/pendingDelete comparison.

## Why It Matters
Previously a reflection was local-only: `getOrFetch` checked Room, and on a miss generated a brand
new one via the appServer. A second device (or a reinstalled app) had no way to learn a reflection
already existed for a given day, and would independently generate its own — potentially different —
content for the same day. This also blocked MS-663's cross-device/reinstall scenario from being fully
verifiable, since there was no way for a second device to discover an already-locked reporter's actual
briefing content.

## The Primary Key Problem This Ticket Actually Found
`DailyReflectionEntity.id` used to be `"${figureId}_${epochDay}_${tone}_${theme}"` — but `figureId` is
a local Room autoincrement PK, not portable across devices. This is a step further than MS-51's
figure-ID translation problem: there, only the `figureId` *field* needed translating through
`serverId`; here, the *primary key itself* embedded that non-portable value, so the row identity
wouldn't even resolve to the same key on a second device.

The fix relies on an invariant already true elsewhere in the codebase: only one figure is ever locked
per `epochDay` (see `getLockedFigureId`/`getFigureIdForDay`). That makes `epochDay_tone_theme` alone a
sufficient, portable natural key — identical in kind to `dayOfWeek` for `day_assignment` — with
`figureId` demoted to an ordinary column translated through `FigureDao.getByServerId()` on pull, same
as MS-51. `MIGRATION_26_27` recomputes the `id` of every existing local row to the new format via a
single `UPDATE ... SET id = epochDay || '_' || tone || '_' || theme`; this is not a *fix* for a bug
that shipped, it's a schema correction applied before this feature's first release, since the sync
path (and thus a portable ID) didn't exist until now.

## Client Changes

### Room Schema — Version 26 → 27
- `daily_reflection` gains `synced: Boolean = false`; the `id` column is recomputed to drop the
  `figureId` prefix.
- `sync_meta` gains `lastDailyReflectionSyncUserId: String?`.
- `MIGRATION_26_27` — one `ALTER TABLE ADD COLUMN`, one `UPDATE` to recompute existing ids, one more
  `ALTER TABLE ADD COLUMN` — registered in both `DatabaseBuilder.android.kt` and
  `DatabaseBuilder.ios.kt`.

### No Tombstones
Unlike `day_assignment`, there is no `pendingDelete`/tombstone column and no `clear()`-equivalent
operation. Reflections are never deleted or edited once generated — the ticket's premise (and the
MS-660 audit that recommended it) is that reconciliation only needs to handle concurrent-*create*, not
concurrent-*edit*. `getPendingSync()`/`markSynced()` exist for the push side; the pull side uses a new
`insertIfAbsent()` (`OnConflictStrategy.IGNORE`) so a pulled row can never clobber content already
generated locally under the same key — the regression test
`resolve_neverOverwritesAnExistingLocalReflectionWithAPulledDuplicate` guards this directly.

### `DailyReflectionRemoteDataSource` — Mirrors `DayAssignmentRemoteDataSource`
Same shape: a small interface (`push`/`fetchAll`, no `delete` — nothing is ever deleted) wrapping
Postgrest calls, with `PostgrestDailyReflectionRemoteDataSource` as the real implementation.
Registered in Koin only when Supabase credentials are configured, alongside the existing
`DayAssignmentRemoteDataSource` binding.

### `DailyReflectionRepositoryImpl.resolve(userId)`
Three phases when signed in, mirroring `DayAssignmentRepositoryImpl.syncWithRemote`:
1. **Account-switch guard**: if a *different* user previously synced reflections on this device, wipe
   local rows first (`SyncMetaEntity.lastDailyReflectionSyncUserId`).
2. **Push pending**: everything with `synced = false` gets pushed; success marks it synced, failure
   leaves it for the next pass.
3. **Pull + union**: fetch all remote rows for the user; each is inserted locally only if absent
   (`insertIfAbsent`) after translating `figureServerId` back to a local `figureId` via
   `FigureDao.getByServerId()` — a row whose figure hasn't synced locally yet is skipped, same failure
   mode as MS-51.

When signed out, `resolve(null)` is a no-op beyond flipping `isResolved` — there's no local-seed
analog for reflections (nothing is generated ahead of demand), so unlike `DayAssignmentRepository`
there's no signed-out branch that writes anything.

`getOrFetch()` still generates on a cache miss exactly as before, then pushes the new row immediately
(mirroring `assign()`'s push-on-write in MS-51) rather than waiting for the next `resolve()`.

### `isResolved` + `BriefingViewModel` Gating
Added `isResolved: StateFlow<Boolean>` to `DailyReflectionRepository`, following the MS-663 pattern.
`BriefingViewModel.loadCard()` folds both `dayAssignmentRepository.isResolved` and
`dailyReflectionRepository.isResolved` into its `combine(...)` pipeline as live inputs (see Result below
for why this isn't a one-time pre-subscribe await) — without also waiting on the reflection signal, a
cold start on a second device could read an empty local reflection table before the pull completed and
generate its own, different reflection for a day another device had already briefed, directly violating
this ticket's first acceptance criterion.

### `AppViewModel` Wiring
The existing single sequential `authState` collector (established in MS-663) now calls
`dayAssignmentRepository.resolve(userId)` and `dailyReflectionRepository.resolve(userId)` back to back
for the same resolved `userId`, inside the same `collect` lambda — keeping both resolutions
sequenced together rather than racing in independent coroutines, for the same reason MS-663 collapsed
day-assignment's seed/sync paths into one collector.

## Supabase Side
Added `supabase/migrations/0002_daily_reflection_sync.sql` — table + `auth.uid() = user_id` RLS
policy, keyed `(user_id, epoch_day, tone, theme)`. **This has to be run once, manually, via the
Supabase SQL editor**, same as MS-51's migration.

## Result

Manual two-device validation of AC 1 (generate on one device, open a second device signed into the
same account, expect the same reflection) initially failed: each device generated its own,
independent briefing despite the Supabase migration having been run and the devices being opened
sequentially with a real gap between them.

**Root cause**: `resolve(userId)` — and its remote pull — only runs **once per distinct signed-in
session** (gated by `distinctUntilChanged()` on `userId` in `AppViewModel`). `getOrFetch`'s
generate-or-not decision only ever checked the *local* Room cache, never the remote, so any run where
a device's one-time pull happened before another device's push had landed (including a device whose
process was still running from an earlier launch, rather than a genuinely fresh cold start) would
still fall through to generating its own duplicate — with no periodic or foreground-triggered resync
to catch it afterward.

**Fix**: `getOrFetch` now checks the remote directly, at the exact moment it decides whether to
generate — not just relying on the once-per-session `resolve()` pull. On a local cache miss, before
calling the appServer, it does a targeted `fetchOne(userId, epochDay, tone, theme)` against Supabase;
if another device already pushed a matching row, that content is adopted and cached locally instead of
generating a new one. This closes the race regardless of session timing, foreground-resume staleness,
or pull ordering — the decision point itself is now authoritative, not a snapshot taken earlier in the
session. `DailyReflectionRemoteDataSource` gained a `fetchOne` method alongside `push`/`fetchAll` for
this.

A secondary theory investigated and ruled out: `BriefingViewModel` passes `figure.serverId` (not the
local Room id) into `getOrFetch`, which looked like it could break the `figureDao.getById(row.figureId)`
translation added for sync. Confirmed this is currently harmless — `FigureEntity.id` and `serverId` are
set to the identical value on every insert (`FigureDto.toEntity()`), so the two are interchangeable
today. Flagging as fragile-but-not-broken: if a future change ever lets local ids diverge from server
ids, this call site would need to pass the local id and let the repository translate to server id
internally at the network boundary, instead of pre-translating at the call site.

**Second bug found (side effort, same session)**: the fallback reporter (the default figure shown so
the briefing card is never null) visibly flashed before the real, server-synced figure replaced it, on
every normal app open — not just the cross-device race above. Root cause: `isResolved` was a one-shot
latch. On a fresh install, `resolve(null)` fires first (the `authState` collector briefly passing
through a signed-out state before the persisted session loads), seeds fallback defaults, and flips
`isResolved` true immediately. `BriefingViewModel`'s one-time `.first { it }` await unblocked right
then and rendered the fallback figure. Moments later `resolve(userId)` fires for the real session and
pulls the actual schedule/reflections, correcting the underlying data that `BriefingViewModel` was
already live-collecting — but since `isResolved`'s *value* didn't change (already `true`), there was no
signal to prefer a loading state during that correction, so the fallback figure just silently swapped
for the real one.

**Fix**: both `DayAssignmentRepositoryImpl.resolve()` and `DailyReflectionRepositoryImpl.resolve()` now
flip `_isResolved.value = false` at the *start* of every resolve pass, not just before the first one.
`BriefingViewModel.loadCard()` folds `isResolved` into its `combine(...)` pipeline as a live input
(`LoadInputs`) instead of awaiting it once before subscribing — so a mid-session correction now shows
`CardState.Loading` again instead of silently swapping stale content for the real data. Regression test:
`loadCard_showsLoadingAgainInsteadOfStaleDataWhenResolutionRestartsMidSession`.

## What We Didn't Do (and Why)
- **A bounded fetch window**: `fetchAll(userId)` pulls every reflection ever generated for the
  account, with no date-range limit. MS-51 also fetches all 7 rows of `day_assignment`, but that table
  is bounded by construction (one row per weekday); `daily_reflection` grows unbounded over the life of
  an account. Left unbounded for this ticket since no AC requires paging and the existing local table
  isn't paged either — revisit if a reported large-account sync becomes slow.
- **appServer persistence**: the ticket's Relevant Files list mentioned adding persistence to
  `DailyReflectionRoutes`, but that would duplicate MS-51's already-established direct-to-Supabase
  precedent and give appServer a new per-user-state responsibility it doesn't have today. Followed
  MS-51's actual architecture instead; `DailyReflectionRoutes` is untouched.
