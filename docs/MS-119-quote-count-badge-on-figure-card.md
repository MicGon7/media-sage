# MS-119: Quote Count Badge on Figure Card

## What was built

Added a reactive quote count badge to each figure card in the Voices screen. The badge appears in the top-right corner and shows collected quote counts (e.g. **3 Qs**) only when the user has at least one cached encouragement for that figure.

## Implementation

### 1. EncouragementDao — `countByFigureName()`

Added a new `@MapInfo`-annotated query that returns a `Flow<Map<String, Int>>` mapping each figure name to its encouragement count:

```kotlin
@MapInfo(keyColumn = "figureName", valueColumn = "count")
@Query("SELECT figureName, COUNT(*) AS count FROM encouragements GROUP BY figureName")
fun countByFigureName(): Flow<Map<String, Int>>
```

`@MapInfo` is required for Room to correctly map the two-column result into a `Map<K, V>`. It has been available since Room 2.4 and is fully supported in KMP Room 2.7.1.

### 2. VoiceFigureItem — `quoteCount` field

Added `quoteCount: Int = 0` to the presentation model in `FiguresContract.kt`. The default keeps all existing code valid without changes.

### 3. FiguresViewModel — reactive combine

Updated `FiguresViewModel` to inject `EncouragementDao` and `combine` the two flows:

```kotlin
combine(
    figureRepository.getAllFigures(),
    encouragementDao.countByFigureName()
) { figures, counts ->
    figures.map { figure ->
        VoiceFigureItem(
            name = figure.name,
            role = figure.role,
            imageUrl = figure.portraitUrl,
            quoteCount = counts[figure.name] ?: 0
        )
    }
}.collect { items -> _state.value = FiguresContract.UiState.Success(figures = items) }
```

The ViewModel already uses `EncouragementDao` directly in other places in the app (e.g. `HistoryViewModel`, `BookmarksViewModel`) — no need to go through a repository for this read-only reactive query.

### 4. FiguresScreen — badge composable

Wrapped `VoiceCard`'s `Surface` in a `Box` so the badge can be positioned at `Alignment.TopEnd` using `offset`. The badge only renders when `quoteCount > 0`:

```kotlin
if (figure.quoteCount > 0) {
    Text(
        text = "${figure.quoteCount} Qs",
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-20).dp, y = 2.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
```

## Tests

Added `FiguresViewModelTest.kt` with four test cases:
- Success state emitted with correct figure names/roles
- `quoteCount` is 0 when no encouragements cached
- `quoteCount` reflects actual cached counts per figure
- `quoteCount` updates reactively when the counts Flow emits a new value

All existing `FakeEncouragementDao` implementations in `EncouragementRepositoryTest` and `HistoryViewModelTest` were updated to implement the new `countByFigureName()` method.

## Verified

- `./gradlew allTests` — 124 tasks, BUILD SUCCESSFUL
- `./gradlew detekt` — BUILD SUCCESSFUL
