# MS-73: You Tab Shell

## What Was Built

Added a third bottom nav tab ("You") to Media Sage, modeled after the NYT "You" tab pattern.

## Files Changed

### New Files
- `feature/you/YouContract.kt` — MVI contract with `Ready` UiState; no intents needed for shell
- `feature/you/YouViewModel.kt` — Minimal ViewModel emitting `Ready` state immediately
- `feature/you/YouScreen.kt` — Greeting header, subheader, Saved + History nav buttons, cog icon
- `feature/settings/SettingsScreen.kt` — Shell placeholder with back navigation
- `feature/bookmarks/BookmarksScreen.kt` — Shell placeholder with back navigation
- `feature/history/HistoryScreen.kt` — Shell placeholder with back navigation

### Modified Files
- `navigation/Routes.kt` — Added `Route.You`, `Route.Bookmarks`, `Route.History`, `Route.Settings`; registered all in `navSerializersModule`
- `navigation/TopLevelDestination.kt` — Added `YOU` entry with `Icons.Filled/Outlined.Person`
- `navigation/MediaSageAppState.kt` — Added `navigateToBookmarks()`, `navigateToHistory()`, `navigateToSettings()`
- `navigation/MediaSageScaffold.kt` — Wired all four new routes to their screens
- `di/AppModule.kt` — Registered `YouViewModel`
- `strings.xml` — Added `nav_you`, `you_greeting`, `you_activity_subheader`, `you_nav_saved`, `you_nav_history`, `you_settings_icon_description`, and all shell screen strings

## Key Decisions

**Greeting hardcoded to "Good morning"** — The ticket explicitly deferred time detection. Both morning/afternoon string keys exist if a future ticket wants to add time-based switching.

**Cog icon inside YouScreen content** — No TopAppBar is used in the project (FiguresScreen/HomeScreen render headers as content). The cog IconButton sits in the top-right of the header row, consistent with that pattern.

**Shell screens have back buttons** — Settings, Bookmarks, and History all render a simple header row with an ArrowBack `IconButton` + title, plus a centered "coming soon" placeholder. This gives the user a visual escape hatch even though the Android back gesture also works.

**No bottom bar on Bookmarks/History/Settings** — `AppState.showBottomBar` returns true only for top-level destinations. Pushing Bookmarks/History/Settings onto the backstack naturally hides the bottom bar, which is the correct UX behavior.

**YouViewModel is intentionally trivial** — Every screen in the project follows the MVI Contract pattern. Even though there is no async data to load for this shell, a ViewModel was added to stay consistent and make future expansion (user profile, activity feed) a natural extension rather than a refactor.

## Patterns Introduced

None — this ticket follows all existing patterns:
- MVI Contract (Contract / ViewModel / Screen)
- Koin `viewModel { }` registration in `AppModule`
- `Route` sealed interface + `navSerializersModule` registration
- `TopLevelDestination` enum entry
- String resources for all visible text
