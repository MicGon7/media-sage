# MS-14: Wire Match Screen to Encourage API

## What Changed
Connected the Match (Headline Detail) screen to the server's encourage endpoint. When a user taps a headline, the app calls Claude to find an encouraging quote, figure, scripture reference, and tone. Also made the server base URL configurable and added a hero image.

## Data Flow

```
User taps headline
  → Navigation pushes Route.Match(headlineId)
    → Koin creates MatchViewModel(headlineId, repo, api) via parametersOf
      → init { loadMatch() }
        → HeadlineRepository.getHeadlineById()     // Get title from Room
          → MediaSageApi.encourage(headlineTitle)   // Call server
            → Server calls Claude API
              → Claude returns quote, figure, scripture, tone
            → EncourageResultDto
          → Map to MatchContract.UiState.Success
        → MatchScreen displays result
```

## Koin Parameter Injection

Instead of using `LaunchedEffect` to trigger loading (which is a workaround), the `headlineId` is injected directly into the ViewModel constructor via Koin's `parametersOf`:

```kotlin
// AppModule.kt
viewModel { (headlineId: Long) -> MatchViewModel(headlineId, get(), get()) }

// Scaffold
val vm = koinViewModel<MatchViewModel>(
    key = "match-${route.headlineId}",
    parameters = { parametersOf(route.headlineId) }
)
```

The `key` parameter ensures each headline gets a fresh ViewModel instance. Without it, Koin caches the first ViewModel and reuses it for all headlines.

The ViewModel loads data in `init` — no LaunchedEffect needed. This is the Koin equivalent of Hilt's assisted injection / SavedStateHandle pattern.

## Configurable Server URL

```
local.properties (not in version control):
  server.base.url=http://192.168.1.100:8080

build.gradle.kts:
  reads local.properties, defaults to http://10.0.2.2:8080 (emulator)
  buildConfigField("String", "SERVER_BASE_URL", ...)

MediaSageApplication.kt:
  sharedModule(BuildConfig.SERVER_BASE_URL)
```

For physical device testing, set the Mac's LAN IP in local.properties and rebuild.

## Hero Image

The Match screen now shows a full-width hero image (200dp) above the headline when an image URL is available from the News API.

## Client-Side API Changes

### New DTOs
- `EncourageRequestDto` — headlineTitle, locale, articleText
- `EncourageResultDto` — summary, quoteText, figureName, figureRole, scriptureReference, scriptureText, explanation, connectionThemes, matchTheme, tone

### MatchContract Updates
- Removed `LoadMatch(headlineId)` intent — init handles loading
- Added: headlineImageUrl, summary, scriptureText, tone
- Error state uses shared ErrorType

## Other Changes

- StatusPages: generic exception handler now logs stack trace
- TODO references: all updated to correct ticket numbers (MS-14, MS-44, MS-45)
- Network security: broadened for physical device testing (TODO MS-44)
- Old match endpoint DTOs and API method deprecated

## Files Changed/Created

### New
- `docs/MS-14-wire-match-screen.md`

### Modified
- `composeApp/build.gradle.kts` — BuildConfig with local.properties server URL
- `composeApp/androidMain/MediaSageApplication.kt` — uses BuildConfig.SERVER_BASE_URL
- `composeApp/androidMain/res/xml/network_security_config.xml` — broadened for dev
- `composeApp/commonMain/di/AppModule.kt` — MatchViewModel with parametersOf
- `composeApp/commonMain/feature/match/MatchContract.kt` — new fields, ErrorType
- `composeApp/commonMain/feature/match/MatchScreen.kt` — hero image, error handling
- `composeApp/commonMain/feature/match/MatchViewModel.kt` — real API wiring
- `composeApp/navigation/MediaSageScaffold.kt` — koinViewModel with key + params
- `composeApp/composeResources/values/strings.xml` — match error messages
- `shared/data/remote/ApiDtos.kt` — EncourageRequestDto, EncourageResultDto
- `shared/data/remote/MediaSageApi.kt` — encourage() method
- `shared/data/remote/MediaSageApiImpl.kt` — encourage() implementation
- `shared/data/repository/MatchRepositoryImpl.kt` — TODO references updated
- `composeApp/feature/figures/FiguresViewModel.kt` — TODO references updated
- `server/plugins/StatusPages.kt` — error logging
