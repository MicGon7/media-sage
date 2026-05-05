# MS-140: Sages screen — search bar, pinned figure first, pin icon to TopStart

## What changed

Added live search to the Figures/Sages screen, sorted the list pinned-first then alphabetically, and repositioned the pin icon from `BottomEnd` to `TopStart` to mirror the quote count chip at `TopEnd`.

## Files touched

- `composeApp/src/commonMain/kotlin/com/mediasage/feature/figures/FiguresContract.kt`
- `composeApp/src/commonMain/kotlin/com/mediasage/feature/figures/FiguresViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/mediasage/feature/figures/FiguresScreen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/commonTest/kotlin/com/mediasage/feature/figures/FiguresViewModelTest.kt`

## Key decisions

### Combining 4 flows with `combine`

The ViewModel previously combined 3 flows (figures, counts, pinnedId). Adding search required a fourth (`_searchQuery: MutableStateFlow<String>`). `kotlinx.coroutines.flow.combine` has a direct overload for 4 flows, so no structural change was needed — just add `_searchQuery` as the fourth argument and extend the lambda.

```kotlin
combine(
    figureRepository.observeAllFigures(),
    encouragementRepository.observeCountByFigureName(),
    pinnedFigureRepository.observePinnedFigureId(),
    _searchQuery
) { figures, counts, pinnedId, query -> ... }
```

### Sort order: pinned first, then alphabetical

Sorting is done inside the combine transform, not in the composable, so the ViewModel always emits a correctly ordered list regardless of search state:

```kotlin
filtered.sortedWith(
    compareByDescending<VoiceFigureItem> { it.isPinned }.thenBy { it.name }
)
```

`compareByDescending { isPinned }` puts `true` (pinned) before `false`. `thenBy { name }` gives alphabetical order within each group.

### No category filter chips

Figures span multiple categories (a theologian is also a mystic), so chips would force arbitrary labels. Free-text search covers the same use case without clutter.

### Pin icon positioning

The original `BottomEnd` placement was visually inconsistent with the quote count chip at `TopEnd`. Moving to `TopStart` mirrors the chip layout — same offset pattern (`offset(x = 20.dp, y = 2.dp)`) to bleed the icon slightly inside the card corner from the opposite side.

### OutlinedTextField for the search bar

`OutlinedTextField` with `MaterialTheme.shapes.medium` matches the card border style and newspaper aesthetic. A trailing clear button (×) appears only when the query is non-blank, keeping the UI clean.

## Test coverage

New tests added to `FiguresViewModelTest`:

- `filtersByNameCaseInsensitive` — "aug" matches "Augustine"
- `filtersByRoleCaseInsensitive` — "APOLOGIST" matches "Author & Apologist"
- `clearingQueryRestoresFullList` — empty query after a search shows all figures
- `pinnedFigureSortsBeforeAlphabeticallyEarlierFigure` — "Zwingli" (pinned) appears before "Augustine"
- `unpinnedFiguresSortAlphabetically` — three unpinned figures sort A→Z

All 109 tests pass. Detekt clean.
