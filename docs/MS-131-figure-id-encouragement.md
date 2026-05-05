# MS-131: Add figureId to EncouragementEntity and replace getByFigureName with getByFigureId

## What Changed

`EncouragementEntity` previously stored `figureName` as a plain string with no FK relationship to `FigureEntity`. Lookups in `FigureDetailViewModel` used `getByFigureName`, which is fragile — any display name change silently breaks the join. This ticket adds a proper `figureId: Long?` foreign key to tie encouragements to the canonical figure record.

## Key Decisions

**`figureId` is nullable.** Existing cached rows have no figureId. Making it non-null would require a destructive migration or a complex backfill. Nullable is safe: the DAO query `WHERE figureId = :figureId` simply returns nothing for old rows until they're re-cached.

**`figureId` is populated on cache write.** After the Claude API returns an encouragement with a `figureName`, `EncouragementRepositoryImpl` calls `figureDao.getByName(figureName)` to resolve the figure's ID before inserting. This is a single extra SQLite read per cache miss — acceptable cost.

**`FigureDetailViewModel` uses `EncouragementRepository`, not `EncouragementDao` directly.** The ViewModel previously bypassed the repository and queried the DAO directly. This ticket moves it to the repository interface, which is the correct architectural layer.

**Navigation passes `figureId`, not `figureName`.** `Route.FigureDetail` carries a `Long` figureId. `FigureDetailViewModel` receives it via Koin `parametersOf`, calls `getFigureById` on the figure repository, then `getByFigureId` on the encouragement repository. No string-based lookup anywhere in the chain. If the figure isn't found (e.g., figures not yet synced), the coroutine exits via `return@launch` and the screen stays in Loading — a guard clause, not an error state.

## Files Changed

| File | Change |
|------|--------|
| `EncouragementEntity.kt` | Added `figureId: Long? = null` |
| `Migrations.kt` | Added `MIGRATION_13_14`: `ALTER TABLE encouragements ADD COLUMN figureId INTEGER` |
| `MediaSageDatabase.kt` | Version bumped 13 → 14 |
| `DatabaseBuilder.android.kt` / `ios.kt` | Added `MIGRATION_13_14` |
| `EncouragementDao.kt` | Added `getByFigureId(figureId: Long): Flow<List<EncouragementEntity>>` |
| `EncouragementRepository.kt` | Added `getByFigureId(figureId: Long): Flow<List<Encouragement>>` |
| `EncouragementRepositoryImpl.kt` | Injected `FigureDao`; implemented `getByFigureId`; populate `figureId` on cache write |
| `EntityMappers.kt` | Added `figureId: Long? = null` param to `Encouragement.toEntity()` |
| `FigureDetailViewModel.kt` | Constructor takes `figureId: Long`; queries `getFigureById` then `getByFigureId`; no DAO dependency |
| `AppModule.kt` | Koin binding uses `parametersOf(figureId: Long)` and injects `EncouragementRepository` |
| `SharedModule.kt` | Updated `EncouragementRepositoryImpl` binding to include `FigureDao` |
| `Routes.kt` | `FigureDetail` route carries `figureId: Long` instead of `figureName: String` |
| `MediaSageAppState.kt` | `navigateToFigureDetail(figureId: Long)` |
| `MediaSageScaffold.kt` | Passes `route.figureId` to Koin `parametersOf` |
| `FiguresContract.kt` | `VoiceFigureItem` gains `id: Long` for stable list keys and click routing |
| `FiguresViewModel.kt` | Maps `figure.id` into `VoiceFigureItem` |
| `FiguresScreen.kt` | Click passes `Long`; `items(key = { it.id })` for stable `LazyColumn` keys |

## Room Migration Pattern

```kotlin
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE encouragements ADD COLUMN figureId INTEGER")
    }
}
```

SQLite `ALTER TABLE ADD COLUMN` adds the column as NULL for existing rows — no data loss, no backfill needed.

## Test Coverage

- `populatesFigureIdWhenFigureExistsOnCache` — verifies `figureId` is written when the figure is found
- `getByFigureIdReturnsMappedEncouragements` — verifies the new query path works end-to-end through the repository
- Updated `FakeEncouragementDao` in all three test files to implement `getByFigureId`
