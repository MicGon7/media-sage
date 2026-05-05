# MS-129: Fix home screen showing empty state on launch due to finally block in fetchHeadlines

## What changed

Removed the `finally` block from `HomeViewModel.fetchHeadlines()` and gave each function a single, clear responsibility for state transitions.

## The bug

`fetchHeadlines()` had a `finally` block that set `UiState.Empty` if state was still `Loading` when the coroutine completed:

```kotlin
// BEFORE — broken
finally {
    if (_state.value is HomeContract.UiState.Loading) {
        _state.value = HomeContract.UiState.Empty
    }
}
```

The problem: `finally` fires as soon as `refreshHeadlines()` returns — before Room's table invalidation has propagated to the `collectHeadlines()` Flow. Room writes (`deleteAll` + `insertAll`) trigger asynchronous Flow emissions. The emission with the fresh headlines can arrive *after* `finally` runs, producing a Loading → **Empty** (flash) → Success sequence.

`finally` is for resource cleanup. Using it to drive UI state transitions is the wrong tool.

## The fix

**1. Remove `finally` from `fetchHeadlines()`.**

**2. Move the `Empty` transition into `fetchHeadlines()` directly.** After `refreshHeadlines()` returns, if the state is still `Loading`, the fetch completed with no data — set `Empty` there:

```kotlin
private fun fetchHeadlines() {
    viewModelScope.launch {
        try {
            headlineRepository.refreshHeadlines()
            if (_state.value is HomeContract.UiState.Loading) {
                _state.value = HomeContract.UiState.Empty
            }
        } catch (e: Exception) {
            if (_state.value is HomeContract.UiState.Loading) {
                _state.value = HomeContract.UiState.Error(e.toErrorType())
            }
        }
    }
}
```

If Room emits headlines before this point, `collectHeadlines()` will have already set `Success` and the check is a no-op. If Room stays empty, this is the correct place to resolve `Loading → Empty`.

**3. Simplify `collectHeadlines()`.** No longer needs to combine with a fetch-done signal — it simply ignores empty emissions while `Loading`:

```kotlin
private fun collectHeadlines() {
    viewModelScope.launch {
        headlineRepository.getHeadlines()
            .collect { headlines ->
                val current = _state.value
                val isRefreshing = current is HomeContract.UiState.Success && current.isRefreshing
                if (headlines.isNotEmpty()) {
                    _state.value = HomeContract.UiState.Success(
                        headlines = headlines.map { it.toItem() },
                        briefingCard = lastBriefingCard
                    )
                } else if (current !is HomeContract.UiState.Loading && !isRefreshing) {
                    _state.value = HomeContract.UiState.Empty
                }
            }
    }
}
```

The `else if` branch handles the post-success case where headlines are cleared from Room — a real edge case that should still resolve to `Empty`.

## State ownership after the fix

| Function | State it may set |
|---|---|
| `collectHeadlines()` | `Success`, `Empty` (post-success clear only) |
| `fetchHeadlines()` | `Empty` (fetch returns no data), `Error` |
| `retryLoad()` | `Loading` (UI reset before re-fetch) |
| `refreshHeadlines()` | Copies `isRefreshing` flag on `Success` |

## MVI note

An earlier iteration introduced `private val _isFetchDone = MutableStateFlow(false)` to gate the `Empty` transition. While it fixed the flash, it violated MVI — state that drives UI behavior was living outside `UiState`. The final fix keeps all state transitions inside `UiState` and eliminates the side-channel flow, the `combine`, and the `yield()`.

## What was NOT changed

The pull-to-refresh `refreshHeadlines()` keeps its `finally` block — that one correctly clears `isRefreshing = false` as resource cleanup, which is the intended use of `finally`.

## Key pattern

`finally` is for resource cleanup, not UI state transitions. When a fetch completes and needs to resolve a loading state, do it explicitly at the end of the `try` block after the suspend call returns.
