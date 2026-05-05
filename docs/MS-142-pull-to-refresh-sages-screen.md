# MS-142: Add Pull-to-Refresh on Sages Screen

## What was built

Added a pull-to-refresh gesture to the Sages (Voices/Figures) screen that triggers a figure delta sync without restarting the app. The refresh indicator shows while the sync is in-flight and dismisses on completion — success or failure.

## Files changed

- `FiguresContract.kt` — added `Refresh` intent and `isRefreshing: Boolean` to `UiState.Success`
- `FiguresViewModel.kt` — added `refresh()` that guards on `Success` state, sets `isRefreshing = true`, calls `syncFigures()`, then sets `isRefreshing = false`
- `FiguresScreen.kt` — wrapped `VoicesList` in a `Box` with `.pullToRefresh()` modifier and `PullToRefreshDefaults.Indicator`
- `FiguresViewModelTest.kt` — added tests for the `Refresh` intent and updated `FakeFigureRepository` to track `syncCallCount`

## Pattern used

Follows the same pattern as `HomeScreen` / `HomeViewModel`:

- `pullToRefresh` modifier on a `Box` (not `PullToRefreshBox`) — this is the API present in the project
- `rememberPullToRefreshState()` + `PullToRefreshDefaults.Indicator` overlaid at `Alignment.TopCenter`
- Refresh is non-fatal: `runCatching { figureRepository.syncFigures() }` swallows errors, matching app-launch behavior

## Refresh guard

`refresh()` early-returns if state is not `Success` — prevents a race where a refresh fires before the initial figure load completes. State is read again after `syncFigures()` returns to pick up any Room-driven updates from the `combine` collector before clearing `isRefreshing`.

## Testing

Two new ViewModel tests:
- `refreshSetsIsRefreshingTrueThenFalse` — verifies `isRefreshing` is false after intent resolves (UnconfinedTestDispatcher runs everything synchronously)
- `refreshCallsSyncFigures` — verifies `syncCallCount == 1` after `Refresh` intent

`FakeFigureRepository` gained a `syncCallCount` counter to support these assertions.

## Device testing

Manual device testing (AC item 4) requires a portrait change in Supabase while the app is running — this is a human verification step requiring a real device and live backend.
