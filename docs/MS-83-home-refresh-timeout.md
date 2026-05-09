# MS-83: Fix pull-to-refresh spinner and align ViewModel error handling to idiomatic patterns

## Summary

The pull-to-refresh spinner on the Home screen either hung indefinitely or dismissed in milliseconds with no user feedback when the network was unavailable. This ticket traces the root causes, corrects them, and aligns `HomeViewModel` and `FiguresViewModel` to the patterns used in Google's NowInAndroid reference.

## Root causes (in order of discovery)

### 1. `Channel.RENDEZVOUS` blocked the `finally` block

The original `_sideEffects` channel was created with the default capacity (`Channel()`), which is `RENDEZVOUS`. `send()` on a RENDEZVOUS channel suspends until a collector is ready to receive. Since no collector was active when `refreshHeadlines()` threw, `_sideEffects.send(ShowError(...))` suspended indefinitely inside the `finally` block — so `isRefreshing = false` never ran and the spinner never dismissed.

**Fix:** `Channel(Channel.BUFFERED)` — `send()` enqueues and returns immediately regardless of whether a collector is active.

**Rule added to CLAUDE.md:** Always use `Channel.BUFFERED` for side effect channels. RENDEZVOUS is the wrong default for fire-and-forget UI events.

### 2. `collectHeadlines()` overwrote `isRefreshing` on every DB emission

`collectHeadlines()` collected from `observeHeadlines()` and always emitted a fresh `Success` state, discarding any existing field values. So even after fix #1 allowed `isRefreshing = false` to run, a simultaneous Room emission would immediately overwrite it with `isRefreshing = false` from a brand-new state — making the spinner appear to dismiss in milliseconds.

**Fix:** Read `isRefreshing` from the current state before building the new `Success` and pass it through:

```kotlin
.collect { headlines ->
    if (headlines.isNotEmpty()) {
        val current = _state.value
        val isRefreshing = current is HomeContract.UiState.Success && current.isRefreshing
        _state.value = HomeContract.UiState.Success(
            headlines = headlines.map { it.toItem() },
            briefingCard = lastBriefingCard,
            isRefreshing = isRefreshing,
            todayLabel = todayLabel()
        )
    }
}
```

### 3. Side effects were never collected — `ShowError` was silently dropped

`HomeViewModel` sent `SideEffect.ShowError` on refresh failure, but `HomeScreen` had no `LaunchedEffect` collecting `sideEffects`. The snackbar message was enqueued and never shown.

**Fix:** Collect side effects in the `Route.Home` NavEntry in `MediaSageScaffold` via `LaunchedEffect(vm)`, feeding into the scaffold-level `SnackbarHostState`. The screen stays stateless — side effect handling belongs at the call site, not inside the screen composable.

## What `withTimeout` in a ViewModel gets wrong

The autonomous agent's first attempt wrapped `refreshHeadlines()` with `withTimeout(15_000L)`. This is non-idiomatic for two reasons:

1. **Wrong layer.** Network timeout logic belongs in the HTTP client — `OkHttp readTimeout` on Android, Ktor's `HttpTimeout` plugin on both platforms. The ViewModel should not know or care how long a network call takes.
2. **Doesn't work on a physically dead connection.** `withTimeout` cancels the coroutine, but a native socket read blocks an IO thread at the OS level. Cancelling the coroutine does not unblock the thread — the socket read continues until the OS gives up (which can take minutes).

OkHttp `readTimeout` and `connectTimeout` interrupt the IO thread directly and are the correct fix for physical network loss.

## Why try/catch/finally is not the NowInAndroid pattern

NowInAndroid ViewModels do not use try/catch or try/catch/finally. Their patterns are:

- **Reactive flows:** `.catch {}` operator or `.asResult()` extension wrapping emissions in a `Result` sealed class
- **One-shot fire-and-forget calls:** `viewModelScope.launch { repo.doThing() }` with no error handling

For one-shot calls where failure needs user feedback, `runCatching` is the idiomatic Kotlin alternative to try/catch/finally — it makes the success/failure branches explicit and removes the need for `finally` as a state-reset mechanism:

```kotlin
viewModelScope.launch {
    val current = _state.value
    if (current is HomeContract.UiState.Success) {
        _state.value = current.copy(isRefreshing = true)
    }
    runCatching { headlineRepository.refreshHeadlines() }
        .onFailure { e ->
            _sideEffects.send(HomeContract.SideEffect.ShowError(e.message ?: "Failed to refresh headlines"))
        }
    val updated = _state.value as? HomeContract.UiState.Success ?: return@launch
    _state.value = updated.copy(isRefreshing = false)
}
```

The `isRefreshing = false` line runs sequentially after `runCatching` completes — no `finally` needed.

A follow-up ticket (MS-155) tracks the full migration to `.asResult()` across all ViewModels.

## Changes made

**`HomeViewModel.kt`**
- `Channel.BUFFERED` on `_sideEffects`
- `collectHeadlines()` preserves `isRefreshing` from current state
- `refreshHeadlines()` uses `runCatching` instead of try/catch/finally

**`MediaSageScaffold.kt`**
- Added scaffold-level `SnackbarHostState` and `snackbarHost`
- `Route.Home` NavEntry collects `vm.sideEffects` via `LaunchedEffect(vm)` and shows snackbar on `ShowError`
- `Route.Figures` NavEntry wired the same way

**`FiguresContract.kt`**
- Added `SideEffect` sealed interface with `ShowError`

**`FiguresViewModel.kt`**
- Added `Channel.BUFFERED` `_sideEffects`
- `refresh()` uses `runCatching` instead of try/catch/finally

**`ui/MediaSageErrorState.kt`** (new)
- Extracted the private `ErrorState` composable from `HomeScreen` into the shared `ui/` package as `MediaSageErrorState`, matching the `MediaSageLoadingState` convention

**`HttpClientFactory.android.kt`**
- OkHttp `connectTimeout`, `readTimeout`, `writeTimeout` set at the engine level (correct layer for physical network loss)

**`HttpClientFactory.ios.kt`**
- Removed invalid `requestTimeout` property (does not exist on Darwin engine); Ktor's `HttpTimeout` plugin in the common factory covers iOS socket timeouts

## Patterns confirmed

- Side effects are collected at the NavEntry call site via `LaunchedEffect(vm)`, not inside the screen composable
- Screen composables accept only `state`, `onIntent`, and navigation lambdas — never a `SnackbarHostState` or side effect flow
- `Channel.BUFFERED` is the correct default for all side effect channels
- Network timeouts belong in the HTTP client, not the ViewModel
- `runCatching` replaces try/catch/finally for one-shot suspend calls with error feedback
- Always verify patterns against NowInAndroid before implementing — do not assume try/catch in ViewModels is idiomatic
