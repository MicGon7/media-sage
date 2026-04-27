# MS-33: Home Screen (Headlines List) Layout

## What Changed
Replaced the placeholder Home screen with the newspaper-styled headlines feed matching the Figma designs.

## Screen Layout

### Masthead
- App name "The Media Sage" in displaySmall serif
- Tagline in italic body text
- Navy rule line divider

### Headline List Items
- Thumbnail image (left, 80dp) via `HeadlineImage` from MS-31
- Category label in small caps with navy color
- Bold serif title (up to 3 lines)
- Snippet in body small (up to 2 lines)
- Source name in navy
- Thin rule dividers between items

### States
- **Loading**: Centered `CircularProgressIndicator`
- **Success**: Masthead + scrollable headline list
- **Error**: Error message + Retry button

## Architecture Changes

### HomeContract
Added `category`, `snippet`, and `publishedAt` fields to `HeadlineItem`.

### HomeViewModel
Initialized with sample headlines so the layout is visible before real API data is wired (MS-13).

### MediaSageAppState
Added `showTopBar` property — returns `false` for the Home route since the masthead replaces the TopAppBar.

### MediaSageScaffold
TopAppBar conditionally shown based on `appState.showTopBar`.

## Design Decisions
- Masthead is inside the `LazyColumn` so it scrolls with content — feels more like a newspaper
- TopAppBar hidden on Home screen to avoid duplicate titles
- `showTopBar` lives in `MediaSageAppState` to keep conditional logic out of the Scaffold
- Sample data in ViewModel lets us validate the layout before API integration
