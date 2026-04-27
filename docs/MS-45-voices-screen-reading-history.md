# MS-45: Redesign Voices Screen to Show Figures from Reading History

## What Changed

Replaced the seed-figure approach with a live list of figures drawn from the user's reading history (`EncouragementEntity`). Added a figure detail screen with a hero portrait, Wikipedia bio, and a quotes bottom sheet.

## Key Decisions

### Reading History as the Data Source
The Voices list is driven by distinct figures Claude has selected in past matches, queried from `EncouragementEntity` via a `GROUP BY figureName` query. No seed figures. Empty state: "Voices will appear as you read."

### headlineTitle Added to EncouragementEntity
Added `headlineTitle: String = ""` to `EncouragementEntity` (Room v5). Passed from `EncouragementRepositoryImpl` to the cache insert, enabling quote context ("In response to: {headline}") on the figure detail screen. Room uses `fallbackToDestructiveMigration` — version bump (4→5) is all that's needed.

### VoiceFigureProjection for DAO Query
Room returns custom projection data classes from queries when field names match column aliases. `MAX()` aggregation handles multiple appearances of the same figure — preferring a non-null `figureImageUrl` over null if the figure appeared before MS-57:

```sql
SELECT figureName, MAX(figureRole) AS figureRole, MAX(figureImageUrl) AS figureImageUrl
FROM encouragements
GROUP BY figureName
ORDER BY figureName ASC
```

### Wikipedia Bio (Client-Side)
`WikipediaRepositoryImpl` fetches bios using the same Wikipedia API already used server-side for portraits, adding `prop=extracts&exintro=true&exsentences=5&explaintext=true`. Fetched once in `FigureDetailViewModel.init`. Attribution: "Sourced from Wikipedia" rendered below the bio.

### Hero Portrait
Full-width 300dp hero image at the top of the detail screen. `ContentScale.Crop` + `Alignment.TopCenter` (same pattern as MS-57). Falls back to a centered `FigurePlaceholder` on a `primaryContainer` background when no portrait URL exists.

### Figure Name as Title
`FigureDetailScreen` hoists `MediaSageBackRow` outside the state `when()` block and fades in the figure name as a title once `Success` arrives — same pattern as `MatchScreen` showing `matchTheme`.

### Quotes Bottom Sheet
`ModalBottomSheet` lists all quotes for the figure from Room. Each row shows the quote and the headline that triggered it. Sheet open/close is `rememberSaveable` local UI state — not in the ViewModel, since it's a purely presentational concern.

### Navigation
`Route.FigureDetail(figureName: String)` — figure name is the natural navigation key since `EncouragementEntity` has no stable integer ID for figures. Updated `navSerializersModule` to include the new route.

### Previews
Used `PreviewParameterProvider` to render all UiState variants in a single `@Preview` — Loading, Success variants, and Error. Previews live at the bottom of each screen file (not in separate files) so developers see both implementation and previews in one place.

## Files Changed

### shared module
- `EncouragementEntity` — added `headlineTitle: String = ""`
- `EncouragementDao` — added `getDistinctFigures()` and `getByFigureName()`
- `VoiceFigureProjection` — new projection data class
- `MediaSageDatabase` — version 4 → 5
- `EntityMappers` — `toEntity(articleUrl, headlineTitle = "")`
- `EncouragementRepositoryImpl` — passes `headlineTitle` to cache insert
- `WikipediaRepository` — new interface
- `WikipediaRepositoryImpl` — new implementation using Ktor client
- `SharedModule` — added `WikipediaRepository` binding

### composeApp module
- `FiguresContract` — replaced `FigureItem` with `VoiceFigureItem`, removed category filter
- `FiguresViewModel` — wired to `EncouragementDao` via Koin
- `FiguresScreen` — portrait in card, empty state, clickable rows, "Gathered Voices" header; previews at bottom
- `FigureDetailContract` — new
- `FigureDetailViewModel` — new
- `FigureDetailScreen` — new (hero portrait, bio, quotes bottom sheet, previews at bottom)
- `Routes` — added `FigureDetail(figureName: String)`
- `MediaSageAppState` — added `navigateToFigureDetail()`
- `MediaSageScaffold` — wired Figures and FigureDetail routes via `koinViewModel`
- `AppModule` — added `FiguresViewModel` and `FigureDetailViewModel`
- `strings.xml` — added empty state, biography label, Wikipedia attribution, quotes plural, "Gathered Voices" header
