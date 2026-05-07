# MS-143: Dark/Light Theme Toggle — Developer Settings

## What was built

A runtime dark/light mode toggle for the **You tab Developer section**, persisted via DataStore so it survives app restarts. The toggle immediately re-themes the entire app without requiring a restart.

## Key decisions

### `datastore-preferences-core` not `datastore-preferences`

The ticket referenced `androidx.datastore:datastore-preferences`. The correct KMP-compatible artifact is `androidx.datastore:datastore-preferences-core` (version 1.1.x), which added multiplatform support in 1.1.0. The `-core` suffix is the multiplatform artifact; the non-core variant is Android-only.

### Platform-specific Koin modules follow the `databaseModule` pattern

DataStore requires a platform-specific file path (Android uses `Context.filesDir`, iOS uses `NSDocumentDirectory`). Rather than expect/actual functions, two separate `val themeModule` definitions were added — one in `androidMain/di/ThemeModule.android.kt` and one in `iosMain/di/ThemeModule.ios.kt` — following the exact same pattern as the existing `databaseModule`. Each is registered in its respective entry point (`MediaSageApplication`, `MainViewController`).

### `isDebugBuild` expect/actual on `Platform.kt`

The Developer section guard required a multiplatform DEBUG flag. `expect val isDebugBuild: Boolean` was added to `Platform.kt`:
- Android actual: `BuildConfig.DEBUG`
- iOS actual: `Platform.isDebugBinary` (Kotlin/Native built-in)

### Theme state lifted to `AppViewModel`

`AppViewModel` observes `ThemePreferencesRepository.darkMode` as a `StateFlow<Boolean>` with `SharingStarted.Eagerly`. `App.kt` collects it and passes it directly to `MediaSageTheme(darkTheme = darkMode)`. This keeps theme control at the root composable level.

### `YouViewModel` owns the write path

`YouViewModel` injects `ThemePreferencesRepository` and handles `YouContract.Intent.ToggleDarkMode`. The `YouContract.UiState.Ready` was promoted from a `data object` to a `data class` to carry `darkMode: Boolean`, so the Switch stays in sync without a separate state holder.

### No mock data toggle added

The ticket description referenced "the existing mock data toggle" but that row does not exist in YouScreen. The AC only explicitly requires the dark mode toggle, so only that was implemented. The mock data toggle can be its own ticket.

## Files changed

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added `datastore = "1.1.2"` + `datastore-preferences` library entry |
| `composeApp/build.gradle.kts` | Added `libs.datastore.preferences` to `commonMain` |
| `Platform.kt` | Added `expect val isDebugBuild: Boolean` |
| `Platform.android.kt` | `actual val isDebugBuild = BuildConfig.DEBUG` |
| `Platform.ios.kt` | `actual val isDebugBuild = Platform.isDebugBinary` |
| `ThemePreferencesRepository.kt` (new) | DataStore wrapper exposing `darkMode: Flow<Boolean>` + `setDarkMode()` |
| `ThemeModule.android.kt` (new) | Koin singleton creating DataStore with Android `Context.filesDir` |
| `ThemeModule.ios.kt` (new) | Koin singleton creating DataStore with iOS `NSDocumentDirectory` |
| `MediaSageApplication.kt` | Added `themeModule` to Koin setup |
| `MainViewController.kt` | Added `themeModule` to Koin setup |
| `AppViewModel.kt` | Added `darkMode: StateFlow<Boolean>` observed from `ThemePreferencesRepository` |
| `App.kt` | Collects `appViewModel.darkMode`, passes to `MediaSageTheme(darkTheme = darkMode)` |
| `AppModule.kt` | Updated `AppViewModel` and `YouViewModel` Koin registrations |
| `YouContract.kt` | `Ready` → `data class Ready(darkMode: Boolean)`, added `ToggleDarkMode` intent |
| `YouViewModel.kt` | Observes `themeRepo.darkMode`, handles `ToggleDarkMode` intent |
| `YouScreen.kt` | Developer section with `isDebugBuild` guard, dark mode `Switch` row |
| `strings.xml` | Added `dev_section_header` and `dev_dark_mode_label` |
| `AgentLaunchService.kt` | Fixed pre-existing `MaxLineLength` detekt violation (incidental) |

## Test results

- `./gradlew :agent:test :server:test :shared:test :composeApp:testDebugUnitTest` — all pass
- `./gradlew detekt` — clean
