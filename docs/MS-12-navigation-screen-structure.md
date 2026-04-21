# MS-12: Navigation & Screen Structure

**Epic:** MS-3 (App UI)
**Date completed:** 2026-04-20

## What was built

Navigation 3 framework with MVI feature-based architecture for the Compose Multiplatform app.

### Navigation
- **`navigation/Routes.kt`** — Sealed interface `Route` with `Home`, `Match(headlineId)`, `Figures`. Polymorphic serialization for KMP state saving.
- **`navigation/TopLevelDestination.kt`** — Enum of bottom nav tabs (Headlines, Figures) with route, label resource, and icon.
- **`navigation/MediaSageAppState.kt`** — Centralizes navigation state: `isTopLevel` (back button), `titleRes` (screen title), navigation methods.
- **`navigation/MediaSageScaffold.kt`** — Top-level Scaffold driven by AppState. MediaSageBottomBar iterates TopLevelDestination declaratively.

### Feature modules (MVI Contract pattern)
Each feature has 3 files:

| File | Purpose |
|------|---------|
| `Contract.kt` | UiState (sealed) + Intent (sealed) + SideEffect (sealed) |
| `ViewModel.kt` | Processes intents, emits state via StateFlow + side effects via Channel |
| `Screen.kt` | Composable UI, observes state, dispatches intents |

- **`feature/home/`** — Headlines feed (HomeContract, HomeViewModel, HomeScreen)
- **`feature/match/`** — Quote match (MatchContract, MatchViewModel, MatchScreen)
- **`feature/figures/`** — Figures browser (FiguresContract, FiguresViewModel, FiguresScreen)

### Resources
- **`composeResources/values/strings.xml`** — All UI strings externalized

## Architecture

```
App() → MaterialTheme → MediaSageScaffold(appState)
  ├── TopAppBar
  │     title = stringResource(appState.titleRes)
  │     back button visible when !appState.isTopLevel
  ├── MediaSageBottomBar(TopLevelDestination.entries)
  └── NavDisplay(appState.backStack)
        ├── Route.Home → HomeViewModel → HomeScreen(state, onIntent, onNavigateToDetail)
        ├── Route.Match → MatchViewModel → MatchScreen(headlineId, state, onIntent)
        └── Route.Figures → FiguresViewModel → FiguresScreen(state, onIntent)
```

## Key decisions & why

- **MVI Contract pattern**: UiState + Intent + SideEffect in a single `Contract.kt` per feature. Less file noise than separate files, conceptually grouped as the "contract" between ViewModel and Screen.
- **Sealed interfaces for UiState**: Loading, Success, Error are distinct types. No invalid state combinations. Transparent loading overlay modeled as `Success(isRefreshing = true)`.
- **Channels for side effects**: One-off events via `Channel` → `receiveAsFlow()`. Guarantees exactly-once delivery when UI is active. Chose over state-based "consumed events" to avoid clearing ceremony boilerplate.
- **`state` not `uiState`**: Inside a ViewModel, there's no ambiguity — the UiState type name already says it's UI state. Shorter, cleaner.
- **No base ViewModel**: Three ViewModels don't justify a generic abstraction. Convention (same file structure) provides consistency.
- **Stateless screens**: Screens receive `state`, `onIntent`, and navigation lambdas — no ViewModel dependency. Previewable and testable. ViewModel wiring happens in MediaSageScaffold's NavDisplay entries.
- **TopLevelDestination enum**: Bottom nav tabs defined as enum with route, label, and icon. Bottom bar iterates entries declaratively — adding a tab is one enum entry.
- **MediaSageAppState**: Centralizes navigation logic. `isTopLevel` drives back button visibility, `titleRes` provides screen title, navigation methods handle all routing. Pattern from Google's Now in Android sample.
- **Scaffold at top level only**: Screens are content composables. No nested Scaffolds.
- **String resources**: All UI strings externalized in `composeResources/values/strings.xml`.
- **Feature packages, not Gradle modules**: Convention-based structure for now. Defer submodules until codebase grows.

## Concepts learned

- **Nav3 in KMP**: JetBrains fork provides NavDisplay. User-owned back stack as a list. `NavEntry(route) { Content() }` for each destination.
- **MVI Contract object**: Nests UiState, Intent, SideEffect as inner sealed interfaces. Usage: `HomeContract.UiState.Loading`, `HomeContract.Intent.LoadHeadlines`.
- **Side effects debate**: Google's Manuel Vivo argues one-off events are anti-patterns — model everything as state. Counter-argument: state-based consumed events add boilerplate and semantic confusion. Channels work well for navigation and snackbars where UI must be active.
- **`SavedStateConfiguration`**: Required for Nav3 state persistence across process death on non-JVM platforms.

## Gotchas

- Material Icons require `compose.materialIconsExtended` — deprecated, migrate to Material Symbols later.
- `TopAppBar` requires `@OptIn(ExperimentalMaterial3Api::class)`.
- Nav3 online docs showed APIs (`entry()`, `startKey`) that don't match the actual 1.0.0-alpha05 library.
- Back button on top-level destinations crashes the app — fixed by checking `appState.isTopLevel` before showing it.
- `collectAsStateWithLifecycle` is Android-only — use `collectAsState()` in KMP commonMain.
