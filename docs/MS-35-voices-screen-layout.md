# MS-35: Voices Screen Layout

## What Changed
Replaced the placeholder Voices (Figures) screen with a card-based layout matching the Figma designs. Also removed the shared TopAppBar entirely — detail screens now handle their own navigation.

## Voices Screen Layout

### Header
- Large serif title "Spiritual Voices"
- Italic subtitle "Theologians, mystics, and faithful witnesses"
- Navy rule line divider

### Voice Cards
- Figure placeholder (initials circle, 64dp) on the left
- Name in bold serif
- Role in navy italic
- Lifespan in small text
- Description snippet (up to 3 lines)
- Thin outline border on each card

### States
- **Loading**: Centered spinner
- **Success**: Header + scrollable card list
- **Error**: Error message + Retry button

## Navigation Changes

### Removed shared TopAppBar
- Removed `showTopBar` and `titleRes` from `MediaSageAppState`
- Removed TopAppBar from `MediaSageScaffold` entirely
- Top-level screens use their own in-content headers
- Detail screens use `MediaSageBackRow` with optional content slot

### MediaSageBackRow (`ui/BackRow.kt`)
- Reusable back arrow row for detail screens
- Content slot for optional title or animated content
- Match screen uses it with fade-in theme title

### Match Screen Updates
- Added `matchTheme` field to contract (e.g., "Community & Purpose")
- Back row title fades in from match theme when data loads
- Screen title changed from "Quote Match" to "Today's Word" in strings
- `onNavigateBack` callback added to screen signature

## FiguresContract Changes
Added `role`, `lifespan`, and `description` fields to `FigureItem`.
