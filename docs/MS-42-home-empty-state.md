# MS-42: Home Screen Empty State

## What was built

Added a meaningful empty state to the Home screen that displays when the server returns zero headlines or Room is empty after a successful fetch.

## Problem

The Home screen had only three states: `Loading`, `Success` (with items), and `Error`. If a fetch succeeded but returned no headlines, the screen remained stuck on `Loading` indefinitely because `collectHeadlines()` skipped empty lists and never transitioned state.

## Solution

### 1. New `UiState.Empty` in the contract

Added `data object Empty : UiState` to `HomeContract.UiState` alongside the existing states.

### 2. Two-path empty detection in HomeViewModel

The empty state can be reached via two code paths:

**Path 1 — Initial fetch returns nothing:**
`fetchHeadlines()` now has a `finally` block that emits `Empty` if state is still `Loading` after the fetch completes. This covers the case where Room was empty before the fetch and the server also returned no results.

```kotlin
} finally {
    if (_state.value is HomeContract.UiState.Loading) {
        _state.value = HomeContract.UiState.Empty
    }
}
```

**Path 2 — DB becomes empty while showing content:**
`collectHeadlines()` now also handles the empty list case, but only when not in `Loading` state (to avoid racing with an in-progress fetch):

```kotlin
} else if (_state.value !is HomeContract.UiState.Loading) {
    _state.value = HomeContract.UiState.Empty
}
```

### 3. EmptyState composable in HomeScreen

Added a centered `EmptyState` composable with a message and a `Refresh` button that dispatches `Intent.RefreshHeadlines`. Styled with `MaterialTheme.colorScheme.onSurfaceVariant` to match the existing design language.

## Testing

Added `HomeViewModelTest` in `composeApp/commonTest` with four tests:
- `emitsEmptyWhenFetchSucceedsAndDbHasNoHeadlines` — core empty state path
- `emitsSuccessWhenDbHasHeadlines` — success path unchanged
- `emitsEmptyWhenDbBecomesEmptyAfterHavingContent` — reactive empty via Flow update
- `refreshFromEmptyStateTriggersRefreshOnRepository` — AC: refresh re-fetches

Tests use `UnconfinedTestDispatcher` + `Dispatchers.setMain` to exercise `viewModelScope`-backed coroutines in commonTest.

Also added `kotlinx.coroutines.test` to `composeApp` `commonTest` dependencies (was already present in `shared`).

## Files changed

| File | Change |
|---|---|
| `HomeContract.kt` | Added `Empty` to `UiState` |
| `HomeViewModel.kt` | `collectHeadlines()` emits Empty on empty-when-not-loading; `fetchHeadlines()` emits Empty in finally when still Loading |
| `HomeScreen.kt` | Added `EmptyState` composable; handles `UiState.Empty` in `when` |
| `strings.xml` | Added `home_empty_message`, `home_empty_refresh` |
| `composeApp/build.gradle.kts` | Added `kotlinx.coroutines.test` to `commonTest` |
| `HomeViewModelTest.kt` | New — 4 tests covering empty state logic |
