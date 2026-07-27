# MS-660: User-Scoped Data Audit

## Purpose

MS-51 shipped Supabase sync for the weekly reporter schedule (`day_assignment`) and established a
reusable *shape*: a per-row `synced`/`pendingDelete` tombstone flag, an account-switch guard keyed
off `SyncMetaEntity`, and push-then-pull reconciliation on sign-in. This document is the full
inventory MS-51 called for but didn't do itself: every piece of data in the app that belongs to one
signed-in account (as opposed to shared catalog/reference data), where it lives today, and whether
MS-51's shape fits it.

**This ticket does not build anything.** It recommends which one concrete item should get its own
sync ticket next, and explicitly argues against generalizing MS-51's shape into a shared abstraction
before a second real implementation exists.

## What counts as "user-scoped"

Data that differs per signed-in account and should follow that account across devices/reinstalls.
Excluded: shared catalog/reference content that's the same for every user — `FigureEntity`,
`QuoteEntity`, `HeadlineEntity`, `MatchEntity` all fall in this bucket (figures/quotes are seeded
from appServer and keyed by `serverId`; headlines/matches are Claude-generated cache content with no
per-user field anywhere in the row). None of the four appear in the inventory below.

## Inventory

### 1. Weekly reporter schedule (`day_assignment`) — already synced (MS-51/MS-661)

- **Storage**: Room, `DayAssignmentEntity`.
- **Sync status**: Live. Reference implementation for everything below.
- **Fit**: N/A — this *is* the pattern.

### 2. Saved insights (bookmarked encouragements) — local-only, revised understanding

- **Storage**: Room, `EncouragementEntity.bookmarked` (`shared/.../entity/EncouragementEntity.kt:28`).
- **Sync status**: Local-only. No sync of any kind today.
- **Revised framing (corrected after product clarification during MS-664 planning)**: this was
  originally analyzed as a pure `(user_id, articleUrl)` join-table case, on the assumption that only
  the `bookmarked: Boolean` flag was user-scoped and the row's content was safe to leave as
  local-cache-only. That's wrong for the actual use case: a user wants to durably save a full
  encouragement match (figure, quote, scripture, explanation, summary) to revisit later — and
  explicitly expects it to survive a device wipe/new phone, not just a flag pointing at content that
  may no longer exist locally. A pure join table can't deliver that, since the pulled `articleUrl`
  wouldn't resolve to anything on a device that never generated that particular match.
- **Fit**: **Fits MS-51's whole-row shape, not the join-table shape** — but with delete support MS-664
  didn't need. Bookmarking must push a full content snapshot (not just a flag); unbookmarking must
  tombstone-delete it, since a user removing something from their saved list expects that removal to
  propagate to other devices too (unlike daily_reflection, which is pure append-only with no delete
  path). `articleUrl` is already a stable, portable natural key — no id-portability problem like
  MS-664 had — but `figureId` still needs the same server-ID translation as MS-51/MS-664 if a saved
  insight should be able to tap through to that figure's detail page. Whether this reuses
  `EncouragementEntity` directly (adding `synced`/sync bookkeeping columns, with push/pull scoped to
  `bookmarked = true` rows only) or a separate dedicated entity is an implementation decision for that
  ticket, not this audit.

### 3. Past briefings / daily reflections — local-only, portable key already

- **Storage**: Room, `DailyReflectionEntity` (`shared/.../entity/DailyReflectionEntity.kt`).
- **Sync status**: Local-only.
- **Shape of the data**: `figureId` here is the same local-autoincrement-PK problem MS-51 solved for
  `day_assignment.figureId` — it isn't portable across devices and would need the same
  `FigureDao.getByServerId()` translation on pull. `epochDay` + `tone` + `theme` (baked into the `id`
  string) are already stable, portable natural keys, same as `dayOfWeek` was for `day_assignment`.
  Reflections are also generated once (Claude call) and effectively immutable afterward — there's no
  local "edit" the way schedule reassignment is an edit, only a create.
- **Fit**: **Fits with small adjustments.** The push/pull/reconcile shape carries over close to
  as-is; the account-switch guard and figure-ID translation are identical in kind. The one
  adjustment: because reflections are append-only (never edited after creation, only ever added),
  the "local unsynced row wins over a pull" rule in MS-51 doesn't need the same nuance — there's no
  concurrent-edit case to protect, only concurrent-create, so reconciliation can be a simpler
  union-by-key rather than a per-row synced/pendingDelete comparison. This is a reasonable **next
  concrete sync ticket** (see Recommendation).

### 4. Reporter "memory quotes" — not yet built

- **Storage**: n/a. No entity, DAO, or repository for this exists anywhere in `shared` today; grepped
  for `memory`/`reporterQuote`-shaped tables and found nothing. This was named in MS-51 as a future
  candidate but hasn't been implemented as a feature yet.
- **Fit**: Can't be judged yet — there's no shape to compare. Flagging so it isn't silently dropped
  from the list; revisit once the feature itself exists.

### 5. Remembered sign-in email — DataStore, outside `shared`

- **Storage**: `composeApp/.../data/AuthPreferencesRepository.kt`, DataStore
  (`user.preferences_pb`), key `remembered_email`.
- **Sync status**: Local-only, and it should stay that way.
- **Fit**: **Doesn't fit, and shouldn't.** This is a device-level login convenience (prefilling the
  email field on the sign-in screen), not account data — it's keyed by *device*, not by the signed-in
  user, and by definition is read *before* any session exists. There is nothing here to push/pull.
  Calling this out per the AC's requirement to surface non-`shared` user-scoped data, but recommending
  no action.

### 6. Theme / dark-mode preference — DataStore, outside `shared`

- **Storage**: `composeApp/.../data/ThemePreferencesRepository.kt`, DataStore
  (`theme.preferences_pb`), keys `dark_mode` and `app_theme`.
- **Sync status**: Local-only.
- **Judgment call (per AC)**: treat this as a **device preference, not an account preference**, and
  do not sync it. Justification: display/appearance settings are the one category where following
  the *device* (e.g. matching a tablet's lighting conditions or a user's per-device taste) is the
  more common mobile UX convention than following the account everywhere — and there's no product
  requirement or user request driving cross-device theme continuity. Revisit only if that changes.
- **Fit**: N/A — recommendation is to leave local-only, not to sync.

### 7. Auth/session state — intentionally out of scope

- **Storage**: `shared/.../data/repository/AuthRepositoryImpl.kt`, backed entirely by the Supabase
  Kotlin SDK's own internal session storage — not a Room table or DataStore file this codebase owns.
- **Sync status**: Handled entirely by the Supabase SDK (session refresh, persistence, multi-device
  sign-in) as a base capability every other item in this inventory is layered on top of.
- **Fit**: Out of scope for this audit. There's no repo-owned row/shape to evaluate — it's the
  substrate, not a sync candidate.

### 8. `SyncMetaEntity` — mixed-concern bookkeeping table, not itself user data

- **Storage**: Room, `SyncMetaEntity` (`shared/.../entity/SyncMetaEntity.kt`).
- **Note**: this single-row table already mixes a global timestamp (`lastFigureSyncAt`, catalog-sync
  bookkeeping, same for every user) with a per-account field (`lastDayAssignmentSyncUserId`). It's
  not itself a sync *candidate* — it's sync *infrastructure* — but every future per-account sync
  ticket following this pattern will add its own `last*SyncUserId`-shaped column here, so it's worth
  watching this table for column sprawl once 2-3 more of these land.

## Recommendation

**Do not build a shared/generic sync abstraction now.** MS-51 is exactly one implementation; MS-664
(daily reflections, item 3) is the second, validating that the whole-row/append-only shape
generalizes. Saved insights (item 2) is a third, different shape — whole-row like MS-51, but scoped to
a subset of rows (only `bookmarked = true`) with real delete support. Three concrete shapes is a
reasonable point to look for what's actually common and extract it — but not before this one ships.

**Shipped: past briefings / daily reflections (item 3), MS-664.**

**Next concrete sync ticket: saved insights (item 2)**, now that the join-table framing has been
corrected to whole-row sync with delete support. `articleUrl` gives it a portable natural key
(cheaper than MS-664's figureId-in-the-PK problem), but the account-switch guard, figure-ID
translation, and tombstone-delete reconciliation all carry over from MS-51 largely as-is.
