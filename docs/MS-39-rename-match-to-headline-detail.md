# MS-39: Rename Match screen to Headline Detail

## Summary

Internal rename of the "Match" screen to "Headline Detail" throughout the UI and navigation layers. No UI or behavior changes — purely a naming correction to reflect that the screen is a headline detail view with a matched quote embedded, not a standalone "match" feature.

## What Changed

### New package: `feature/headlinedetail/`
- `HeadlineDetailContract.kt` — replaces `MatchContract`
- `HeadlineDetailViewModel.kt` — replaces `MatchViewModel`
- `HeadlineDetailScreen.kt` — replaces `MatchScreen`

### Navigation
- `Routes.kt`: `Route.Match` → `Route.HeadlineDetail`
- `MediaSageAppState.kt`: `navigateToMatch()` → `navigateToHeadlineDetail()`
- `MediaSageScaffold.kt`: updated imports, route branch, ViewModel key prefix

### DI
- `AppModule.kt`: updated import and registration for `HeadlineDetailViewModel`

### Resources
- `strings.xml`: `title_match` → `title_headline_detail`, comment updated

## What Was NOT Renamed

The shared module's `Match` domain types (`MatchEntity`, `MatchDao`, `Match`, `MatchRepository`) were intentionally left unchanged. These represent the *concept* of matching a quote to a headline, which is a valid domain term independent of the screen name.

## Key Decision

The `match_*` string keys used inside `HeadlineDetailScreen` (e.g., `match_finding`, `match_retry`) were left unchanged because they describe the *matching process* (finding a wisdom match), not the screen name. Renaming them would be a separate, lower-priority task.
