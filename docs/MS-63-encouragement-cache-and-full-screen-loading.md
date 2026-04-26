# MS-63: Encouragement Cache and Full-Screen Loading

## What Was Done

Replaced the two-phase progressive loading on the match screen with a single full-screen loading state, and added a Room-backed cache for encouragement results so repeat visits to the same headline are instant.

## Changes

### Full-Screen Loading
`MatchViewModel` previously transitioned to `UiState.Success` immediately after loading the headline from Room, then showed `EncouragementState.Loading` while Claude responded. Now it stays in `UiState.Loading` until both headline and encouragement are resolved, then jumps directly to `UiState.Success` with all data populated. `FullLoadingState` was updated to use the Lottie animation instead of a `CircularProgressIndicator`.

### Room Cache (`EncouragementEntity`)
Added `EncouragementEntity` with `articleUrl` as the primary key, and `EncouragementDao` with insert + query-by-URL + deleteAll. `EncouragementRepositoryImpl` checks Room before calling the API — a cache hit skips the network entirely.

### Cache Key: Article URL, Not Headline ID
The first attempt keyed the cache by `headlineId` (Long). This silently broke because:
- `HeadlineEntity` uses `@PrimaryKey(autoGenerate = true)`
- `refreshHeadlines()` calls `headlineDao.deleteAll()` then re-inserts — giving the same articles brand new IDs each refresh
- The cached encouragement was orphaned every time headlines refreshed

**Fix:** Key by `articleUrl` (String). The URL is stable across refreshes — the same article always maps to the same cache entry. Encouragements are no longer wiped on headline refresh since they're URL-scoped and never go stale.

### Encouragement Lifecycle
Encouragements persist indefinitely since they're tied to article URLs, not headline IDs. They naturally become unused when the news rotates (old URLs are no longer in the headline list) but don't need active cleanup — the data is small.

## Key Learnings

### Auto-Increment IDs Are Unstable Cache Keys
Any time you delete and re-insert rows, auto-incremented IDs change. Never use them as cache keys across refresh cycles. Use a natural, stable identifier from the domain — in this case, the article URL.

### Two Debugging Signals That Pointed to the Bug
1. First tap after app restart still showed the loading screen (cache miss)
2. The DB version bump with `fallbackToDestructiveMigration` only wipes once — so the issue was structural, not a migration artifact

### Room Flow + deleteAll
When `deleteAll()` runs, Room emits an empty list to any active collectors. Collectors that guard on `isNotEmpty()` will silently ignore the empty emission, preserving the previous UI state until `insertAll()` emits the fresh data. This is intentional in `HomeViewModel.collectHeadlines()`.
