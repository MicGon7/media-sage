# MS-150: Android Status Bar Icons Invisible in Light Theme

## What Was Fixed

`enableEdgeToEdge()` was called once with no arguments in `MainActivity.onCreate()`. The default
`SystemBarStyle.auto` adapts to the **system** dark/light setting, not the app's custom DataStore
toggle. So when the user toggled light mode inside the app, status bar icons stayed white and became
invisible against the light background.

## Fix

Observe `darkMode` from `AppViewModel` inside `setContent` and call `enableEdgeToEdge()` reactively
via `SideEffect`:

```kotlin
setContent {
    val appViewModel = koinViewModel<AppViewModel>()
    val darkMode by appViewModel.darkMode.collectAsState()

    SideEffect {
        enableEdgeToEdge(
            statusBarStyle = if (darkMode) {
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

### Why `SideEffect`

`SideEffect` runs after every successful recomposition. When `darkMode` flips, Compose recomposes,
`SideEffect` fires, and `enableEdgeToEdge` sets the correct icon style immediately.

### Why the same ViewModel instance works

`koinViewModel<AppViewModel>()` is scoped to the activity's `ViewModelStoreOwner`. Calling it both
in `setContent` and inside `App()` returns the same instance — no duplicate state, no double
initialization.

### `SystemBarStyle` icon rules

- `SystemBarStyle.light(...)` → **dark icons** (visible on light/white background)
- `SystemBarStyle.dark(...)` → **light/white icons** (visible on dark background)

Both take a transparent scrim so the status bar remains translucent over the app content.

## Files Changed

- `composeApp/src/androidMain/kotlin/com/mediasage/MainActivity.kt`
  - Added `SideEffect` to call `enableEdgeToEdge()` with `SystemBarStyle` derived from `darkMode`
  - Added Koin `koinViewModel<AppViewModel>()` collection in `setContent`
