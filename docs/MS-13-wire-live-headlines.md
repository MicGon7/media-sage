# MS-13: Wire Live Headlines Data

## What Changed
Connected the Home screen to live headline data from the News API. This is the first end-to-end data flow in the app, establishing the patterns that all other screens will follow.

## The Big Picture

Before this ticket, the app had three disconnected layers:
- **Server** — working API endpoints that call News API, Claude, and Scripture APIs
- **Shared** — Room database, DAOs, repositories, Ktor client — all defined but not connected
- **UI** — screens with sample data, no real data flow

This ticket connects them into a single pipeline.

## Data Flow (Room as Single Source of Truth)

```
                                    ┌──────────────┐
                                    │  News API    │
                                    │  (external)  │
                                    └──────┬───────┘
                                           │
                                    ┌──────▼───────┐
                                    │  Ktor Server │
                                    │  /api/news/  │
                                    │  headlines   │
                                    └──────┬───────┘
                                           │
                              ┌────────────▼─────────────┐
                              │  MediaSageApi            │
                              │  (Ktor Client in shared) │
                              └────────────┬─────────────┘
                                           │
                              ┌────────────▼─────────────┐
                              │  HeadlineRepositoryImpl  │
                              │  refreshHeadlines()      │
                              │  DTO → Entity → Room     │
                              └────────────┬─────────────┘
                                           │ writes to
                              ┌────────────▼─────────────┐
                              │  Room Database           │
                              │  (single source of truth)│
                              └────────────┬─────────────┘
                                           │ Flow emits
                              ┌────────────▼─────────────┐
                              │  HeadlineRepositoryImpl  │
                              │  getHeadlines() : Flow   │
                              └────────────┬─────────────┘
                                           │
                              ┌────────────▼─────────────┐
                              │  HomeViewModel           │
                              │  collectHeadlines()      │
                              └────────────┬─────────────┘
                                           │
                              ┌────────────▼─────────────┐
                              │  HomeScreen              │
                              │  (Compose UI)            │
                              └──────────────────────────┘
```

### Why Room as Single Source of Truth?
The UI **never reads from the API directly**. Instead:
1. `fetchHeadlines()` calls the API and writes results to Room
2. `collectHeadlines()` observes Room via Flow and updates UI state
3. Any database change automatically propagates to the UI

This gives us:
- **Offline support** — cached headlines display even when the server is down
- **Single data stream** — the UI only needs to observe one source
- **Consistency** — no race conditions between API and cache

## Koin Dependency Injection

### How Koin Wires Everything

Koin is a service locator / DI framework. You register providers in modules, then request instances with `get()`. The dependency chain for `HomeViewModel`:

```
HomeViewModel
  └── HeadlineRepository (interface)
        └── HeadlineRepositoryImpl
              ├── HeadlineDao (from Room database)
              │     └── MediaSageDatabase
              │           └── getDatabaseBuilder(context).build()
              └── MediaSageApi (interface)
                    └── MediaSageApiImpl
                          ├── HttpClient (Ktor, platform-specific engine)
                          └── serverBaseUrl (String)
```

### Module Structure

**Platform Database Module** (`shared/androidMain/di/DatabaseModule.android.kt`):
```kotlin
val databaseModule = module {
    single<MediaSageDatabase> { getDatabaseBuilder(get()).build() }
    //                                              ^^^
    //                          Koin injects Android Context automatically
    //                          because of androidContext() in startKoin
}
```
Android needs `Context` to build Room. iOS doesn't — it uses `NSHomeDirectory()`.

**Shared Module** (`shared/commonMain/di/SharedModule.kt`):
```kotlin
fun sharedModule(serverBaseUrl: String = "http://10.0.2.2:8080") = module {
    single { createHttpClient() }
    single<MediaSageApi> { MediaSageApiImpl(get(), serverBaseUrl) }

    // DAOs extracted from the database instance
    single { get<MediaSageDatabase>().headlineDao() }
    single { get<MediaSageDatabase>().figureDao() }
    // ...

    // Repositories — Koin resolves constructor params via get()
    single<HeadlineRepository> { HeadlineRepositoryImpl(get(), get()) }
    //                                                   ^^^   ^^^
    //                                              HeadlineDao  MediaSageApi
}
```

**App Module** (`composeApp/commonMain/di/AppModule.kt`):
```kotlin
val appModule = module {
    viewModel { HomeViewModel(get()) }
    //                        ^^^
    //                   HeadlineRepository
}
```

**Startup** (`MediaSageApplication.onCreate()`):
```kotlin
startKoin {
    androidContext(this@MediaSageApplication)
    modules(databaseModule, sharedModule(), appModule)
}
```

### viewModel {} vs koinViewModel<>()

In the Scaffold, we switched from:
```kotlin
val vm = viewModel { HomeViewModel() }  // Manual construction, no DI
```
to:
```kotlin
val vm = koinViewModel<HomeViewModel>()  // Koin resolves all dependencies
```

`koinViewModel` asks Koin for a `HomeViewModel`. Koin finds the registration in `appModule`, calls `HomeViewModel(get())`, resolves `HeadlineRepository` -> `HeadlineRepositoryImpl(headlineDao, api)` -> and so on down the chain. The ViewModel is still scoped to the composable lifecycle (survives recomposition, cleared on navigation away).

## ViewModel Architecture

### Init: System Events vs User Intents

```kotlin
init {
    collectHeadlines()  // Start observing Room — system event
    fetchHeadlines()    // Trigger API call — system event
}
```

`init` handles system events (app opened, screen appeared). Intents handle user actions:
- `LoadHeadlines` — retry after error (user tapped "Retry")
- `RefreshHeadlines` — pull-to-refresh (user pulled down)

### Two Coroutines, One Data Stream

```kotlin
private fun collectHeadlines() {  // Long-lived: observes Room Flow forever
    viewModelScope.launch {
        headlineRepository.getHeadlines().collect { headlines ->
            _state.value = Success(headlines.map { it.toItem() })
        }
    }
}

private fun fetchHeadlines() {  // Short-lived: one API call, writes to Room
    viewModelScope.launch {
        try {
            headlineRepository.refreshHeadlines()
            // No state update here! Room Flow handles it automatically
        } catch (e: Exception) {
            if (_state.value is Loading) {
                _state.value = Error(e.toErrorType())
            }
        }
    }
}
```

`fetchHeadlines()` doesn't update the UI state on success — it just writes to Room. The `collectHeadlines()` Flow picks up the database change and updates the UI. This is the reactive pattern.

### Retry vs Refresh

Both call `headlineRepository.refreshHeadlines()` but handle state differently:

| | LoadHeadlines (Retry) | RefreshHeadlines (Pull) |
|---|---|---|
| **When** | Error screen, user taps Retry | Success screen, user pulls down |
| **Before** | Sets state to Loading (spinner) | Sets isRefreshing = true (pull indicator) |
| **On success** | Room Flow updates UI | Room Flow updates UI |
| **On failure** | Shows error screen | Shows snackbar, keeps existing headlines |

### Error Handling

```
ErrorType.kt (shared in ui/)
├── ErrorType enum: NETWORK, GENERIC
└── Exception.toErrorType(): maps IOException -> NETWORK, else -> GENERIC

HomeScreen resolves ErrorType -> string resource:
├── NETWORK -> "Unable to reach the server..."
└── GENERIC -> "Something went wrong..."
```

ErrorType lives in `ui/` so all ViewModels can reuse `toErrorType()`.

## Pull-to-Refresh

Uses Material3 `pullToRefresh` modifier with `PullToRefreshDefaults.Indicator`:
- Wraps the LazyColumn in a Box with the pull gesture
- `isRefreshing` state from ViewModel controls the indicator
- `onRefresh` sends `RefreshHeadlines` intent

## DTO -> Entity Mapping

`NewsArticleDto.toEntity()` converts the API response to a Room entity:
- `imageUrl` blank string -> null (API returns empty string, entity stores nullable)
- `publishedAt` uses `fetchedAt` as fallback (proper ISO parsing deferred)
- `fetchedAt` stamped with current time via `currentTimeMillis()` expect/actual

## Platform-Specific Code

### currentTimeMillis() (expect/actual)
KMP doesn't have `System.currentTimeMillis()` in common code:
- **Android**: `System.currentTimeMillis()`
- **iOS**: `NSDate().timeIntervalSince1970 * 1000`

### Network Security (Android)
Android 9+ blocks HTTP (cleartext) traffic. `network_security_config.xml` allows cleartext only to `10.0.2.2` (emulator localhost) and `localhost`.

## Testing Locally

1. Start the server: `source ~/.zshrc && ./gradlew :server:run`
2. Run the app on Android emulator (uses `http://10.0.2.2:8080`)
3. Headlines should appear from the News API
4. Stop the server — cached headlines still display (offline mode)
5. Clear app data + stop server — error state with friendly message
6. Pull down on headline list — refresh indicator appears, fresh data loads

## Files Changed/Created

### New Files
| File | Purpose |
|------|---------|
| `composeApp/androidMain/MediaSageApplication.kt` | Android Application with Koin startup |
| `composeApp/commonMain/di/AppModule.kt` | ViewModel Koin registrations |
| `composeApp/commonMain/ui/ErrorType.kt` | Shared error classification |
| `composeApp/androidMain/res/xml/network_security_config.xml` | Allow cleartext to localhost |
| `shared/androidMain/di/DatabaseModule.android.kt` | Android Room database provider |
| `shared/iosMain/di/DatabaseModule.ios.kt` | iOS Room database provider |
| `shared/androidMain/data/repository/CurrentTimeMillis.android.kt` | Platform time |
| `shared/iosMain/data/repository/CurrentTimeMillis.ios.kt` | Platform time |

### Modified Files
| File | Change |
|------|--------|
| `shared/di/SharedModule.kt` | Added DAO providers, API injection into repositories |
| `shared/data/repository/HeadlineRepositoryImpl.kt` | Implemented refreshHeadlines() with API call |
| `shared/data/mapper/EntityMappers.kt` | Added NewsArticleDto.toEntity() mapper |
| `composeApp/feature/home/HomeViewModel.kt` | Injected HeadlineRepository, real data flow |
| `composeApp/feature/home/HomeContract.kt` | Error state uses shared ErrorType |
| `composeApp/feature/home/HomeScreen.kt` | Error type resolution, pull-to-refresh |
| `composeApp/navigation/MediaSageScaffold.kt` | HomeViewModel via koinViewModel() |
| `composeApp/androidMain/AndroidManifest.xml` | Application class, INTERNET permission, network security |
| `composeApp/composeResources/values/strings.xml` | Error messages |
