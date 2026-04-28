# MS-64: Diversify Figure Selection by Passing Recent Figures to Claude

## What Was Built

Added a sliding window of recently used figure names that is sent to Claude on each encourage request. This prevents the same figures (e.g. Spurgeon, Bonhoeffer) from appearing repeatedly across articles in a session.

## How It Works

1. **`EncouragementEntity.cachedAt`** — new `Long` column (DB version 7) populated with `currentTimeMillis()` on insert. Existing rows default to `0L` (no TTL impact, just sorted last).

2. **`EncouragementDao.getRecentFigureNames(limit: Int)`** — returns up to `limit` distinct figure names ordered by `cachedAt DESC`. Pulled before every API call.

3. **`EncourageRequestDto.recentFigures`** — new field carried from client → server. Defaults to `emptyList()` so the contract is fully backwards-compatible.

4. **`EncouragementRepositoryImpl`** — queries recent figures (N=10) before calling the API and includes them in the request. Uses the existing `currentTimeMillis()` expect/actual in the repository package.

5. **`ClaudeApiService.buildEncourageMessage()`** — when `recentFigures` is non-empty, appends a `## Figure Diversity` section instructing Claude to prefer alternatives. The instruction uses "if a suitable alternative exists" so Claude is never hard-blocked from a strong thematic match.

## Key Decisions

- **N=10**: Enough history to encourage variety without over-restricting Claude's pool.
- **Soft instruction**: "avoid if a suitable alternative exists" — thematic fit always wins over diversity.
- **`cachedAt` default 0L**: Migration-safe; existing rows sort last in the recency window.
- **Client owns history**: No server-side state. Room is the source of truth.
- **Backwards-compatible DTO**: `recentFigures` defaults to `emptyList()` — old clients still work.

## Room Migration

DB bumped from version 6 → 7. Both platforms use `fallbackToDestructiveMigration`, so no explicit SQL migration was needed. Schema 7.json exported.

## Files Changed

| File | Change |
|------|--------|
| `shared/.../entity/EncouragementEntity.kt` | Added `cachedAt: Long = 0L` |
| `shared/.../dao/EncouragementDao.kt` | Added `getRecentFigureNames(limit)` |
| `shared/.../remote/ApiDtos.kt` | Added `recentFigures` to `EncourageRequestDto` |
| `shared/.../repository/EncouragementRepositoryImpl.kt` | Fetch + pass recent figures, set `cachedAt` on insert |
| `shared/.../mapper/EntityMappers.kt` | Added `cachedAt` param to `Encouragement.toEntity()` |
| `shared/.../db/MediaSageDatabase.kt` | Version bumped to 7 |
| `server/.../routes/AnalysisRoutes.kt` | Added `recentFigures` to `EncourageRequest`, threaded to service |
| `server/.../service/ClaudeApiService.kt` | Added `recentFigures` param + Figure Diversity section in prompt |
| `shared/schemas/.../7.json` | New Room schema export |
| `shared/.../EncouragementRepositoryTest.kt` | Added fake impl + new test for recent figures propagation |
