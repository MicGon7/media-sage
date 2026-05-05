# MS-129: Fix home screen showing empty state on launch due to finally block in fetchHeadlines

## What changed

Removed the `finally` block from `HomeViewModel.fetchHeadlines()` and rerouted the `Empty` state transition through `collectHeadlines()`, making it the sole owner of both `Success` and `Empty` transitions.

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

The fix has two parts:

**1. Remove `finally` from `fetchHeadlines()`.**  
The catch block handles errors; nothing else in `fetchHeadlines()` should touch state.

**2. Replace the `Loading` guard in `collectHeadlines()` with a `_isFetchDone` signal.**  
`collectHeadlines()` already guarded against `Loading → Empty` (to prevent flashing empty while the first fetch is in flight). But that guard left the ViewModel stuck in `Loading` forever when the network returned no headlines. The fix introduces a `MutableStateFlow<Boolean>` called `_isFetchDone` that is combined with the headlines Flow:

```kotlin
private val _isFetchDone = MutableStateFlow(false)

// collectHeadlines: combine headlines + fetch-done signal
headlineRepository.getHeadlines()
    .combine(_isFetchDone) { headlines, fetchDone -> Pair(headlines, fetchDone) }
    .collect { (headlines, fetchDone) ->
        if (headlines.isNotEmpty()) {
            _state.value = HomeContract.UiState.Success(...)
        } else {
            val current = _state.value
            val isRefreshing = current is HomeContract.UiState.Success && current.isRefreshing
            if ((fetchDone || current !is HomeContract.UiState.Loading) && !isRefreshing) {
                _state.value = HomeContract.UiState.Empty
            }
        }
    }

// fetchHeadlines: set flag after successful refresh
try {
    headlineRepository.refreshHeadlines()
    yield()                   // let Room's emission from insertAll propagate first
    _isFetchDone.value = true // triggers collectHeadlines to resolve Empty if still Loading
} catch (e: Exception) {
    if (_state.value is HomeContract.UiState.Loading) {
        _state.value = HomeContract.UiState.Error(e.toErrorType())
    }
}
```

The `yield()` gives Room's `insertAll` emission a chance to reach `collectHeadlines()` before `_isFetchDone` flips to true. This means the happy path (network returns headlines) resolves via Room's emission → `Success`, and the flag flip is a no-op. The empty path (network returns no headlines) resolves via the flag → `Empty`.

**3. Reset `_isFetchDone` in `retryLoad()`.** Without the reset, a second attempt after an empty result would find `_isFetchDone = true` and immediately transition to Empty before the new fetch had a chance to run.

## State ownership after the fix

| Function | State it may set |
|---|---|
| `collectHeadlines()` | `Success`, `Empty` |
| `fetchHeadlines()` | `Error` (on exception only) |
| `retryLoad()` | `Loading` (UI reset before re-fetch) |
| `refreshHeadlines()` | Copies `isRefreshing` flag on `Success` |

## What was NOT changed

The pull-to-refresh `refreshHeadlines()` keeps its `finally` block — that one correctly clears `isRefreshing = false` as resource cleanup, which is the intended use of `finally`.

## Key pattern

When a ViewModel combines a database Flow with a coroutine lifecycle event (fetch complete), use `combine` to merge both signals. This keeps the single collector as the state owner and avoids race-prone `finally` blocks driving UI state.
