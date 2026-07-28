# composeApp — UI Module

## UI Architecture (MVI Contract Pattern)

Each feature has 3 files under `composeApp/src/commonMain/kotlin/com/mediasage/feature/{name}/`:

| File | Purpose |
|------|---------|
| `{Name}Contract.kt` | UiState (sealed interface) + Intent (sealed interface) + SideEffect (sealed interface) |
| `{Name}ViewModel.kt` | Processes intents, emits state via StateFlow, side effects via Channel |
| `{Name}Screen.kt` | Stateless composable — receives state, onIntent, and navigation lambdas |

Key conventions:
- **Sealed interfaces for UiState**: Loading, Success, Error — mutually exclusive, no invalid combinations
- **Channels for side effects**: One-off events (navigation, snackbar) via `Channel(Channel.BUFFERED)` → `receiveAsFlow()`. Always use `Channel.BUFFERED` — the default `RENDEZVOUS` capacity causes `send()` to suspend if no collector is active, which blocks `finally` blocks and leaves the UI in a stuck state.
- **State-holder pattern — choose by data source** (follows Now in Android):
  - *Local-only state* — the ViewModel fully owns the values and nothing is derived from a repository (e.g. a wizard step, a selected tab on a static screen): hold a single `_state: MutableStateFlow<UiState>`. Intents read `(_state.value as? UiState.Ready) ?: return` and write back with `_state.value = current.copy(...)`. Simplest correct option — use it when it applies.
  - *State derived from live sources* — UI state is computed from user selection **and** one or more repository/use-case streams that emit on their own schedule (e.g. a calendar built from the selected month plus figure/assignment/briefing streams): use the NiA reactive state-holder. Hold the user selection in a **single input flow** (`MutableStateFlow<SomeInput>`), `combine` it with the domain stream(s), and expose the result with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`. `UiState` is the **derived output** of the combine — you never write to it directly; intents update the input flow with `input.update { it.copy(...) }`, and the combine recomputes. Prefer a use case over injecting the repositories directly when more than one stream is combined (see shared/CLAUDE.md). `ReaderViewModel` is the reference implementation.
  - **Anti-pattern (do not do this):** caching repository emissions in mutable `var` fields — or reading `_state.value` back inside the pipeline — so an intent handler can rebuild derived state by hand. That reintroduces the very state the pattern removes and desyncs from the live streams. If an intent needs live data to recompute, that is the signal to use the reactive state-holder above, not a cache.
- **`state` not `uiState`**: The type name already says UiState
- **Screens are stateless**: Receive state + callbacks, no ViewModel dependency. Previewable and testable.
- **No base ViewModel class**: Convention over abstraction
- **Screen parameters — hard rule**: A screen composable accepts exactly three kinds of parameters: `state`, `onIntent`, and navigation lambdas (`onNavigateTo*`). Nothing else. No booleans, no config, no extras.
- **Ambient config via CompositionLocal**: Values that are needed deep in the tree but are not dynamic state (e.g., `isDebugBuild`) use `CompositionLocal`. Define a `compositionLocalOf { default }` in `commonMain`, provide it once in `App`, read it with `.current` inside the composable. See `LocalIsDebugBuild.kt`.
- **UiState holds UI state, not build config**: Static build-time constants (e.g., debug flags) do not belong in `UiState` or ViewModel. They are ambient environment values, not runtime state.
- **`expect/actual` is for platform API differences only**: Never use `expect/actual` for build config constants (e.g., `isDebugBuild`). Doing so creates duplicate class entries in the Android dex and causes `NoSuchMethodError` crashes when the build cache serves a stale artifact. Pass build config as a `Boolean` parameter from each platform entry point (`MainActivity`, `MainViewController`) down to `App`.

## Navigation (Nav3)

- **`navigation/Routes.kt`** — Sealed interface `Route` with type-safe destinations
- **`navigation/TopLevelDestination.kt`** — Enum of bottom nav tabs with route, label, icon
- **`navigation/MediaSageAppState.kt`** — Centralizes navigation: `isTopLevel`, `titleRes`, navigate methods
- **`navigation/MediaSageScaffold.kt`** — Top-level Scaffold with AppState-driven top bar and bottom bar

## Conventions

- String resources in `composeResources/values/strings.xml` — no hardcoded strings in UI
- Before implementing any Compose effect or Android platform API, verify the approach against NowInAndroid or the official Compose docs. If you find yourself adding a null guard inside a `SideEffect`, you've chosen the wrong effect type.
- Solve problems at the right layer — network timeouts belong in the HTTP client, not the ViewModel. Data validation belongs at the repository boundary, not the UI.
- **`@OptIn` propagates through public signatures**: If a wrapper composable exposes an experimental type anywhere in its parameter list (e.g. `sheetState: SheetState`), every call site must also carry `@OptIn(ExperimentalMaterial3Api::class)` — even when only using the default value. The annotation on the wrapper definition does not cover callers. When adding a new composable that wraps an experimental API, add `@OptIn` to the wrapper *and* document in its KDoc that callers need it too, or hide the experimental type behind a non-experimental default so callers are not exposed.
- **Every new screen or reusable UI component ships with a `@Preview`**: New screen composables and new reusable UI components (cards, carousels, list items, etc.) must include at least one `@Preview` composable in the same file, using realistic sample data — this catches layout, alignment, and color issues in the IDE instead of a full build-deploy-navigate round trip. Group the preview composable(s) under a `// region Previews` block at the bottom of the file.
- **Preview sample data lives in its own `{Screen}StateProvider.kt` file, not inline**: When a screen has more than one UiState variant worth previewing (loading, error, populated, empty), supply them via a `PreviewParameterProvider` and `@PreviewParameter` — and put that provider class in its own file (e.g. `BriefingUiStateProvider.kt` next to `BriefingScreen.kt`), not inline in the screen file. This keeps the screen file focused on production UI and lets the sample data be reused if another preview needs the same states. See `BriefingScreen.kt` / `BriefingUiStateProvider.kt`, `FigureDetailScreen.kt` / `FigureDetailStateProvider.kt`, and `FiguresScreen.kt` / `FiguresStateProvider.kt`.
