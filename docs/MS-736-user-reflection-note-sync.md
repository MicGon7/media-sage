# MS-736: Scope and Sync the User's Own Reflection Note Per Account

## What Changed
`user_reflection_note` (the reader's own typed note, encrypted client-side since MS-737) now
scopes and syncs per signed-in Supabase user, following the same push/pull shape as
`DailyReflectionRepositoryImpl` (MS-664) — but with two departures required by how this table is
actually used.

## Why Two Departures From the MS-664 Template

**Composite `(userId, id)` key instead of a wipe-on-account-switch.** `DailyReflectionRepositoryImpl`
isolates accounts by wiping the whole local table when a different user signs in
(`SyncMetaEntity.lastDailyReflectionSyncUserId`). That works because daily reflections are
disposable server-generated cache. A user's own note is not disposable in the same way, and
`read_headlines` (MS-734) had already fixed the identical "two accounts on one device" leak for
read-status by giving the table a composite `(userId, url)` key instead of a wipe. Reusing that
shape here means isolation holds unconditionally by construction — a query for account A's notes
can never return account B's row — rather than depending on a wipe step running before the next
read.

**Last-write-wins reconciliation instead of insert-if-absent union.** `DailyReflectionRepositoryImpl`
reconciles pulled rows with `insertIfAbsent` because a reflection is generated once and never
edited — any pulled duplicate must lose to whatever already exists locally. A note is edited
repeatedly, including from a second device, so the same table needs to let a genuinely newer edit
win. `pullAndReconcile` compares `updatedAtMillis` and only adopts the remote row when it is newer
than the local copy (or no local copy exists).

## Client Changes

### Room Schema — Version 36 → 37
- `user_reflection_note` gains `userId: String` (now part of the primary key, alongside `id`) and
  `synced: Boolean = false`.
- `MIGRATION_36_37` recreates the table with the composite key. Existing local notes predate any
  account association, so they're bucketed under an anonymous `''` `userId` — same precedent as
  `MIGRATION_34_35`'s `read_headlines` backfill. They become invisible once a real account signs
  in, rather than visible to it; this is an accepted trade-off, not a bug, since there is no way to
  recover which account actually wrote a pre-migration note.

### `UserReflectionNoteRemoteDataSource` / `PostgrestUserReflectionNoteRemoteDataSource`
Mirrors `DailyReflectionRemoteDataSource`: `push`/`fetchAll`, no `delete`. `note_text` carries only
the client-side-encrypted ciphertext MS-737 produces — the Supabase row is never plaintext.

### `UserReflectionNoteRepositoryImpl`
- `getNote`/`saveNote` now scope every DAO call by the current session's `userId` (or `""` when
  signed out), via `AuthRepository.currentSession()`.
- `saveNote` pushes the note immediately after the local upsert (mirroring `getOrFetch`'s
  push-on-write), rather than waiting for the next `resolve()` pass — the note the user just wrote
  should reach the server without an extra sign-in/launch cycle.
- `resolve(userId)` pushes anything unsynced for that user, then pulls and reconciles by
  `updatedAtMillis`. Unlike `DailyReflectionRepositoryImpl`, there's no account-switch wipe step —
  the composite key already makes that unnecessary — and no `isResolved` flag, since no UI screen
  currently gates a loading state on this repository's sync completing.

### `AppViewModel` Wiring
Added `userReflectionNoteRepository.resolve(userId)` to the existing sequential `authState`
collector, alongside `dayAssignmentRepository`, `dailyReflectionRepository`,
`encouragementRepository`, and `quoteRepository`.

## Supabase Side
Added `supabase/migrations/0008_user_reflection_note_sync.sql` — table + `auth.uid() = user_id` RLS
policy, keyed `(user_id, id)`. **Run this once, manually, via the Supabase SQL editor**, same as
every prior sync migration in this series.

## What We Didn't Do (and Why)
- **`isResolved` gating**: `DailyReflectionRepository`/`DayAssignmentRepository` expose an
  `isResolved: StateFlow<Boolean>` so their ViewModels can show a loading state during the first
  resolve pass. Neither `BriefingViewModel` nor `DayDetailViewModel` currently blocks on note sync
  completing before reading — `getNote` just returns whatever is locally present at that moment,
  same as before this ticket. Added if a future ticket needs a loading state here; no AC in this
  ticket requires it.
- **`getNote` checking the remote directly on a cache miss** (the `adoptFromRemote` fix
  `DailyReflectionRepositoryImpl` needed after MS-664 shipped): that fix addressed a specific race
  between a fresh generate and another device's in-flight push. A note's `getNote` never generates
  anything — it only reads whatever `resolve()` has already pulled — so the same race does not
  apply here in the same way. Revisit if manual verification turns up a similar staleness window.
