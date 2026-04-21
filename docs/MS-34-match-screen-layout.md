# MS-34: Match Screen Layout

## What Changed
Replaced the placeholder Match screen with an editorial layout showing a headline matched with an encouraging quote from a Christian figure. Also added `showTopBar` and `showBottomBar` to AppState for proper navigation bar visibility.

## Screen Layout

### Headline Section (top)
- Category label in small caps (navy)
- Headline title in bold serif
- Source name in navy

### Rule Divider
Navy 1dp line separating headline from quote

### Quote Card
- Transparent background with thin outline border (newspaper column style)
- Large opening/closing curly quotation marks in navy
- Quote text in Playfair Display regular
- Figure attribution: initials placeholder + name + role in navy italic

### Scripture Reference
Italic body text below the card

### Match Explanation
Body text explaining the connection between headline and quote

### States
- **Loading**: Spinner + "Finding encouragement..." text
- **Success**: Full editorial layout
- **Error**: Error message + Retry button

## Navigation Changes

### MediaSageAppState
- Added `showTopBar` — hides TopAppBar on Home screen (masthead replaces it)
- Added `showBottomBar` — hides bottom nav on detail screens (Match)

### MediaSageScaffold
- TopAppBar conditionally shown via `appState.showTopBar`
- Bottom nav conditionally shown via `appState.showBottomBar`

## Contract Changes
Added to `MatchContract.UiState.Success`: `headlineSource`, `headlineCategory`, `figureRole`, `scriptureReference`. Removed `confidence` and `connectionThemes`.

## Design Decisions
- Playfair Display regular for quote text (italic was hard to read)
- Transparent card with border instead of elevation — more newspaper-authentic
- Bottom bar hidden on detail screens for full-screen editorial experience
- Sample data in ViewModel for layout validation before API wiring
