# MS-139: Remove Empty State from Home Screen

## What Changed

`HomeContract.UiState.Empty` was removed. The home screen now has three valid states: `Loading`, `Success`, and `Error`.

## Why

Empty is not a valid home screen state. If there are no headlines it means something went wrong (network failure, API error), not a legitimate empty list. The empty state UI was also flashing during certain timing scenarios on cold start.

## Changes

### `HomeContract.kt`
Removed `data object Empty : UiState`.

### `HomeViewModel.kt`
Two places emitted `UiState.Empty`; both now keep the state as-is (staying `Loading`):

1. **`fetchHeadlines()`** — after `refreshHeadlines()` succeeds but Room is still empty, the `if (state is Loading) state = Empty` block was removed. The spinner keeps going until `collectHeadlines()` observes real data.
2. **`collectHeadlines()`** — the `else if (not loading and not refreshing) state = Empty` branch was removed. When Room reports an empty list, state is left unchanged.

### `HomeScreen.kt`
- Removed `is UiState.Empty` branch from the `when` block.
- Removed the `EmptyState` private composable.
- Removed unused `Icons.AutoMirrored.Outlined.List` import.

### `HomeViewModelTest.kt`
Three tests were updated:

| Old name | New name | Assertion changed |
|---|---|---|
| `emitsEmptyWhenFetchSucceedsAndDbHasNoHeadlines` | `staysLoadingWhenFetchSucceedsAndDbHasNoHeadlines` | `Empty` → `Loading` |
| `emitsEmptyWhenDbBecomesEmptyAfterHavingContent` | `staysSuccessWhenDbBecomesEmptyAfterHavingContent` | `Empty` → `Success` |
| `refreshFromEmptyStateTriggersRefreshOnRepository` | `refreshFromLoadingStateTriggersRefreshOnRepository` | `Empty` → `Loading` (precondition) |

## Key Insight

`UiState.Empty` was an intermediate state that only existed because `fetchHeadlines()` completed before `collectHeadlines()` observed the Room update. Rather than modelling that timing gap as a distinct UI state, the fix lets `Loading` cover the window between "fetch complete" and "Room emits data". This eliminates the flash and simplifies the state machine from four states to three.
