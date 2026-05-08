# MS-83: Add timeout and cancellation to pull-to-refresh on Home screen

## Summary

Added a 15-second `withTimeout` guard around `headlineRepository.refreshHeadlines()` in `HomeViewModel` to prevent the pull-to-refresh spinner from hanging indefinitely on slow or unresponsive network calls.

## What changed

**`HomeViewModel.kt`**
- Wrapped both `fetchHeadlines()` and `refreshHeadlines()` with `withTimeout(15_000L)`
- `TimeoutCancellationException` is caught before the generic `Exception` handler in each function
- On initial-load timeout (`fetchHeadlines`): transitions to `UiState.Error(ErrorType.GENERIC)` — same as any other failure during first load
- On pull-to-refresh timeout (`refreshHeadlines`): transitions to `UiState.Error(ErrorType.GENERIC)` so the user sees the error screen with a Retry button instead of a stuck spinner
- Extracted `REFRESH_TIMEOUT_MS = 15_000L` as a file-level constant for readability

**`HomeViewModelTest.kt`**
- Added `refreshDelayMs` parameter to `FakeHeadlineRepository` — uses `delay()` to simulate a slow network, which integrates with the virtual clock in `runTest`
- `fetchHeadlinesTimesOutAndSetsErrorState`: verifies initial-load timeout transitions to `UiState.Error`
- `refreshHeadlinesTimesOutAndTransitionsToErrorState`: verifies pull-to-refresh timeout stops the spinner and transitions to `UiState.Error`
- Both tests use `testScheduler.advanceTimeBy(16_000L)` to advance virtual time past the 15-second threshold

## Why `TimeoutCancellationException` is caught separately

`TimeoutCancellationException` extends `CancellationException` extends `Exception`. A generic `catch (e: Exception)` would catch it, but the error message would be something like "Timed out waiting for 15000 ms" rather than a user-friendly message. More importantly, we want predictable, testable timeout behavior distinct from general network failures.

## Navigating away cancels in-flight coroutines

This was already correct before this ticket — both `fetchHeadlines()` and `refreshHeadlines()` launch on `viewModelScope`. When the ViewModel is cleared (the user navigates away from Home), `viewModelScope` is cancelled, which propagates to all child coroutines. No code change was needed for this AC.

## Patterns used / confirmed

- `withTimeout(ms) { ... }` is the idiomatic way to add a wall-clock deadline to a suspend call in KMP
- Catch `TimeoutCancellationException` *before* the generic `Exception` handler so the two paths are explicitly separated
- `FakeHeadlineRepository(refreshDelayMs = N)` + `testScheduler.advanceTimeBy(M)` is the standard way to test timeout behavior in `runTest` without real wall-clock waits
- File-level `private const` is preferred over a companion object for constants in a file with a single class
