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

### 2. Bookmarked encouragements — local-only, mixed-concern row

- **Storage**: Room, `EncouragementEntity.bookmarked` (`shared/.../entity/EncouragementEntity.kt:28`).
- **Sync status**: Local-only. No sync of any kind today.
- **Shape of the data**: this is the case MS-51's own notes flagged as worth calling out. The row as
  a whole (`summary`, `quoteText`, `scriptureText`, etc.) is *cacheable content* keyed by
  `articleUrl` — every user who matches the same headline gets the same explanation text, so most of
  the row is shared, not user-scoped. Only the single `bookmarked: Boolean` column is per-account.
- **Fit**: **Doesn't fit directly.** MS-51's shape assumes the whole row is user-owned and safe to
  push/pull/tombstone wholesale. Here, syncing the full row would be wrong (two users bookmarking the
  same article shouldn't overwrite each other's cache content) and wasteful (re-pushing match
  explanation text that's identical for everyone). The right shape is a **separate join table** —
  e.g. `bookmarked_encouragements(user_id, article_url)` — that syncs only the `(user, articleUrl)`
  pair, with the encouragement content itself staying local-cache-only exactly as it is today.
  Tombstone deletes and the account-switch guard both still apply to that join table; the "translate
  through a stable server ID" step from MS-51 doesn't apply since `articleUrl` is already a stable,
  portable natural key.

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

**Do not build a shared/generic sync abstraction now.** MS-51 is exactly one implementation; this
audit surfaces two more candidates that fit the pattern well enough to reuse it directly
(reflections) or with a small structural change (bookmarks via a join table) — but "well enough to
reuse the same push/pull/tombstone/account-guard *shape*" is different from "identical enough to
extract a shared base class or generic repository today." Extracting now, before a second concrete
case exists in code, risks guessing the wrong seams (e.g. over-generalizing the join-table case from
bookmarks into something reflections don't need, or vice versa).

**Next concrete sync ticket: past briefings / daily reflections (item 3).** It's the closest fit to
MS-51's existing shape (same figure-ID translation problem, same account-switch guard, straightforward
natural key), making it the cheapest way to validate the pattern generalizes — and only once *that*
ticket is done does it make sense to look for what's actually common between the two and extract it.
Bookmarked encouragements (item 2) is next after that, once the join-table shape has a first
implementation to be modeled on.
