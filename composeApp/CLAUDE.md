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
- **Single `_state` rule**: ViewModel-owned values (selected tab, mode, selected day) live in `UiState.Ready` fields, not in separate `MutableStateFlow`s. Collect repository Flows in `init` and write their results into `_state.value` directly. Intents read the current state with `(_state.value as? UiState.Ready) ?: return` and write back with `_state.value = current.copy(...)`. Never split ViewModel state across multiple private flows to participate in a `combine` — that pattern is reserved for combining multiple repository streams, not for managing state the ViewModel already owns.
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
