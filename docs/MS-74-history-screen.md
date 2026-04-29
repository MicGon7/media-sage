# MS-74: History Screen

## What Was Built

Wired up the History button in the You tab to a fully functioning reading history screen. The screen shows all articles that received an encouragement, newest first, with a tap-to-revisit flow.

## Feature Files

- `composeApp/.../feature/history/HistoryContract.kt` — MVI contract (UiState: Loading / Empty / Success)
- `composeApp/.../feature/history/HistoryViewModel.kt` — Collects `EncouragementDao.getAll()` and resolves headlineId per item
- `composeApp/.../feature/history/HistoryScreen.kt` — Stateless composable with list, empty state, and back navigation

## Data Layer Changes

**`EncouragementDao`** — added:
```kotlin
@Query("SELECT * FROM encouragements ORDER BY cachedAt DESC")
fun getAll(): Flow<List<EncouragementEntity>>
```

**`HeadlineDao`** — added:
```kotlin
@Query("SELECT id FROM headlines WHERE url = :url LIMIT 1")
suspend fun getIdByUrl(url: String): Long?
```

## Navigation Pattern

History items include a `headlineId: Long?` resolved from `HeadlineDao.getIdByUrl(articleUrl)` at load time. If the headline has since been refreshed out of the DB, `headlineId` is null and the tap is a no-op. Otherwise, tapping navigates to `Route.HeadlineDetail(headlineId)` which loads the encouragement from Room cache instantly.

This mirrors the HomeScreen navigation pattern — navigation callback passed directly to the Screen composable, no side effects needed.

## Key Design Decision: Pre-resolve vs On-tap headlineId

The ViewModel resolves `headlineId` during state loading (not on tap). This keeps the Screen stateless and avoids async work in click handlers. The downside is a slightly stale mapping if headlines refresh while the user is viewing History — but since the encouragement is cached by `articleUrl`, the detail screen will still serve the cached content.

## DI Wiring

`HistoryViewModel` injects `EncouragementDao` and `HeadlineDao` directly (no repository layer), following the precedent set by `FiguresViewModel`. This avoids adding new repository methods for a read-only, cache-only feature.

## Tests

`HistoryViewModelTest` covers:
- Empty state when no encouragements cached
- Success state with correctly mapped items
- Quote preview truncation at 120 chars
- `headlineId` populated when headline found by URL
- `headlineId` null when headline not in DB
- State updates when the Flow emits new values
