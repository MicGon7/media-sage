# MS-150: Android Status Bar Icons Invisible in Light Theme

## Root Cause

Two compounding issues caused status bar icons to remain black (light-style) even after the user
switched to dark mode:

1. **Unconditional `enableEdgeToEdge()` before `super.onCreate()`** — A bare `enableEdgeToEdge()`
   with no style args was called at the top of `onCreate`. This set the baseline window state before
   Compose had started, and its interaction with the subsequent `SideEffect` calls prevented the
   correct dark-mode style from persisting.

2. **`StateFlow` initial value of `false` caused a wrong first `SideEffect` call** —
   `AppViewModel.darkMode` was initialized with `stateIn(..., initialValue = false)`. The very
   first composition always saw `darkMode = false`, so `SideEffect` fired with
   `SystemBarStyle.light()` (dark/black icons) before DataStore had emitted the saved preference.
   When DataStore emitted `true`, a second `SideEffect` with `SystemBarStyle.dark()` was supposed
   to override it, but the combination of all three `enableEdgeToEdge` calls — the unconditional
   one, the initial-false one, and the corrective one — meant the third call did not persist.

## Fix

**Two changes:**

### 1. Remove the unconditional `enableEdgeToEdge()` from `MainActivity.onCreate()`

Eliminated the call that created the conflicting baseline state. `enableEdgeToEdge` is now only
called from the reactive `SideEffect`.

### 2. Change `AppViewModel.darkMode` initial value to `null`

```kotlin
// AppViewModel.kt
val darkMode: StateFlow<Boolean?> = themePreferencesRepository.darkMode
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

The `SideEffect` skips calling `enableEdgeToEdge` while the value is null (i.e., until DataStore
emits the real preference). This eliminates the wrong-initial-value call entirely — the first and
only `enableEdgeToEdge` call uses the actual saved preference:

```kotlin
// MainActivity.kt
setContent {
    val appViewModel = koinViewModel<AppViewModel>()
    val darkMode by appViewModel.darkMode.collectAsState()

    SideEffect {
        val isDark = darkMode ?: return@SideEffect
        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            },
        )
    }

    App(isDebugBuild = BuildConfig.DEBUG)
}
```

`App.kt` defaults to `false` while the preference loads (imperceptible — DataStore emits within
a frame or two):

```kotlin
MediaSageTheme(darkTheme = darkMode ?: false)
```

### `SystemBarStyle` icon rules

- `SystemBarStyle.light(...)` → **dark icons** (visible on light/white background)
- `SystemBarStyle.dark(...)` → **light/white icons** (visible on dark background)

Both use a transparent scrim so the status bar remains translucent over app content.

### Why `return@SideEffect` works

`SideEffect` takes a plain `() -> Unit` lambda. `return@SideEffect` is a Kotlin labeled local
return that exits the lambda early — equivalent to an early `return` inside a regular function.

## Files Changed

- `composeApp/src/androidMain/kotlin/com/mediasage/MainActivity.kt`
  - Removed unconditional `enableEdgeToEdge()` before `super.onCreate()`
  - `SideEffect` now skips when `darkMode` is null (before DataStore emits)
- `composeApp/src/commonMain/kotlin/com/mediasage/AppViewModel.kt`
  - `darkMode` changed from `StateFlow<Boolean>` (initial `false`) to `StateFlow<Boolean?>` (initial `null`)
- `composeApp/src/commonMain/kotlin/com/mediasage/App.kt`
  - `MediaSageTheme(darkTheme = darkMode ?: false)` handles the nullable
