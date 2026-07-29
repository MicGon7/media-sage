# MS-51: Sync Weekly Reporter Schedule to Supabase Per User

## What Changed
The weekly reporter schedule (`day_assignment` — 7 rows keyed by day-of-week) now syncs to Supabase per signed-in user, client-side, no appServer involved — the same direct-access pattern `AuthRepositoryImpl` already uses for auth. This is also the template ticket: the pending-write/reconciliation shape here is meant to be copied by the upcoming current-briefing, past-briefings, and reporter-quotes sync tickets.

## Why It Matters
Previously `DayAssignmentEntity` was purely local — a schedule change on one device never showed up on another, and reinstalling lost it. Only `auth-kt` was wired to Supabase before this; there was no Postgrest client, no custom table, and no per-row dirty-flag pattern anywhere in the codebase to copy from.

## The Figure ID Translation Problem
`DayAssignmentEntity.figureId` is a **local Room autoincrement PK** — not portable across devices. `FigureEntity.serverId` is the actual stable identity (synced from appServer via `syncFigures()`). Pushing/pulling schedule rows has to translate through `serverId`, or a schedule would resolve to the wrong figure — or nothing — on a second device or after reinstall. Added `FigureDao.getByServerId()` for the pull-side lookup.

## Client Changes

### Room Schema — Version 25 → 26
- `day_assignment` gains `synced: Boolean = false` and `pendingDelete: Boolean = false`.
- `sync_meta` gains `lastDayAssignmentSyncUserId: String?`.
- `MIGRATION_25_26` — plain `ALTER TABLE ADD COLUMN`s, registered in both `DatabaseBuilder.android.kt` and `DatabaseBuilder.ios.kt`.

### Tombstones Instead of Hard Deletes
`clear(dayOfWeek)` used to hard-delete the row. Now it sets `pendingDelete = true` (a tombstone) and attempts an immediate remote delete; the row is only physically purged once that delete is confirmed. Without this, a delete made while offline could be silently "resurrected" the next time the device pulled — the remote row would still exist and get re-inserted locally as if nothing had happened. All the normal read queries (`observeAll`, `getByDayOfWeek`, `countAll`) filter `pendingDelete = 0`; a new `getRawByDayOfWeek` (no filter) exists specifically so pull-reconciliation can tell a real gap apart from a pending tombstone.

### `DayAssignmentRemoteDataSource` — a New Seam
A small interface (`push`/`delete`/`fetchAll`) wrapping Postgrest calls, with `PostgrestDayAssignmentRemoteDataSource` as the real implementation. Extracted specifically for testability — the project's "no mocking libraries, use Fakes" convention needs a seam to fake, and this one doubles as the shape the next sync ticket can copy. Registered in Koin only when Supabase credentials are configured (same nullable-optional pattern as `SupabaseClient`/`AuthRepository`).

### `DayAssignmentRepositoryImpl.syncWithRemote(userId)`
Three phases, called once per distinct signed-in user (from `AppViewModel`, see below):
1. **Account-switch guard**: if a *different* user previously synced on this device, wipe local rows first (`SyncMetaEntity.lastDayAssignmentSyncUserId` tracks this). A `null` previous value (first sync ever) does **not** trigger a wipe — that would destroy any offline edits made before the very first successful sync.
2. **Push pending**: everything with `synced = false` (including tombstones) gets pushed or deleted remotely; success marks it synced / purges it, failure leaves it for the next pass.
3. **Pull + reconcile**: fetch all remote rows for the user. Empty remote + empty local → brand-new account, fall back to `seedDefaultsIfEmpty()` then push those defaults up. Otherwise, each pulled row is applied *unless* the local row for that day is unsynced or pending delete (a live local edit always wins over a pull, and gets pushed on the next pass instead of being clobbered). Any locally-synced row absent from a non-empty pull is purged (deleted from another device).

`assign()`/`clear()` still push immediately and inline — no batching, per the ticket.

### `AppViewModel` Wiring
A single sequential collector over `authState` (filtering out `Loading`, mapping to a nullable `userId`, `distinctUntilChanged()`) either calls `syncWithRemote(userId)` when authenticated with a real account, or `seedDefaultsIfEmpty()` otherwise (unauthenticated, or the `bypassAuth()` offline path — both map to a `null` userId).

**Bug found during manual verification, fixed before merge**: the first version of this wiring ran `seedDefaultsIfEmpty()` unconditionally in one coroutine and `syncWithRemote(userId)` in a second, independent one reacting to auth state. On a fresh install signed into a real account, these raced: if the unconditional local-seed coroutine won and inserted the fallback defaults first (`synced = false`, since they're a fresh local write), `syncWithRemote`'s pull saw a *non-empty* local table and treated those just-seeded default rows as a pending local edit that should win over the pulled data — silently discarding the real remote schedule. Reproduced by signing in, reassigning Monday, uninstalling, and reinstalling: Monday came back as the hardcoded default (Augustine of Hippo) instead of the real assignment. Fixed by collapsing both paths into one sequential collector so exactly one of them ever runs for a given resolved auth state — no concurrent write race is possible.

**Second bug found in production, fixed in MS-661**: `figureRepository.syncFigures()` still ran in its own independent coroutine, unordered relative to the `authState` collector above. `syncWithRemote`'s pull-reconcile resolves every remote row through `FigureDao.getByServerId()` (per the figure ID translation problem above) — on a fresh install, if that pull ran before figure sync had populated the figures table, every row failed to resolve and `applyRemoteRow` silently dropped it, leaving `day_assignment` completely empty (not retried until the next distinct signed-in session, since `distinctUntilChanged()` only fires once per resolved `userId`). Reproduced on both iOS and Android: fresh install → sign into an account with an existing schedule → the MS-658 reassignment-confirmation lock never engages and Past Briefings shows no briefing for today. Fixed by having the `authState` collector `join()` the figure-sync job before it starts collecting, so figure sync always completes first.

**Third bug found in production, fixed in MS-685**: `synced = true` on a local row was overloaded with two conflicting meanings. `seedDefaultsIfEmpty(markAsSynced = true)` sets it while signed-out purely to stop `pushPending()` from treating a placeholder as a pending edit to push. `purgeMissingFromRemote()` reads that same flag to mean "this row was confirmed on the remote, so its absence from a fresh pull means it was deleted upstream — purge it locally too." On a brand-new account's first real sign-in, the remote fetch is legitimately empty (nothing has ever been pushed), so every locally-seeded default was read as "deleted remotely" and purged, leaving the Reader screen's weekly schedule empty right after sign-up. Reproduced during MS-681 (sign-up flow) testing, though the defect predates that ticket. Fixed by having `resetIfAccountChanged()` report whether this is the account's genuine first-ever sync on this device (a `null` previous `lastDayAssignmentSyncUserId`) and threading that into `pullAndReconcile()`: an empty remote fetch on a first-ever sync is now treated as "adopt whatever's local as the new baseline" (seed if needed, mark everything unsynced, push) rather than "everything local was deleted upstream." An account that has already synced with this device before still gets the original purge behavior when its remote goes empty — the fix only changes the reconciliation decision on a true first sync.

## Supabase Side
No `supabase/migrations` convention existed before this ticket (no RLS SQL was checked into the repo anywhere). Added `supabase/migrations/0001_day_assignment_sync.sql` — table + `auth.uid() = user_id` RLS policy. **This has to be run once, manually, via the Supabase SQL editor** — there's no Supabase MCP/tool in this environment to execute it directly.

## What We Didn't Do (and Why)
- **Conflict resolution beyond last-write-wins**: no `updated_at`-based merge logic. Sync is immediate and single-device-at-a-time in practice; a genuine same-instant two-device conflict just means whichever push lands last on the server wins. Revisit if this becomes a real, reported problem.
- **True OS-level foreground-resume hook**: the ticket's suggested approach mentions retrying on "next app foreground/launch." No foreground hook (`ProcessLifecycleOwner` or equivalent) exists anywhere in `composeApp` yet, and none of the ACs require mid-session resync — launch/sign-in coverage satisfies all five. Adding a foreground observer for this alone would be scope creep; worth doing once a ticket actually needs it.
