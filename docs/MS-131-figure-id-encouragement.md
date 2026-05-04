# MS-131: Add figureId to EncouragementEntity and replace getByFigureName with getByFigureId

## What Changed

`EncouragementEntity` previously stored `figureName` as a plain string with no FK relationship to `FigureEntity`. Lookups in `FigureDetailViewModel` used `getByFigureName`, which is fragile — any display name change silently breaks the join. This ticket adds a proper `figureId: Long?` foreign key to tie encouragements to the canonical figure record.

## Key Decisions

**`figureId` is nullable.** Existing cached rows have no figureId. Making it non-null would require a destructive migration or a complex backfill. Nullable is safe: the DAO query `WHERE figureId = :figureId` simply returns nothing for old rows until they're re-cached.

**`figureId` is populated on cache write.** After the Claude API returns an encouragement with a `figureName`, `EncouragementRepositoryImpl` calls `figureDao.getByName(figureName)` to resolve the figure's ID before inserting. This is a single extra SQLite read per cache miss — acceptable cost.

**`FigureDetailViewModel` uses `EncouragementRepository`, not `EncouragementDao` directly.** The ViewModel previously bypassed the repository and queried the DAO directly. This ticket moves it to the repository interface, which is the correct architectural layer.

**Navigation still passes `figureName`.** Changing navigation route params is a separate concern. The ViewModel still receives `figureName` as its constructor arg, resolves the figure via `FigureRepository.getFigureByName`, extracts the `id`, then queries encouragements by ID. If the figure isn't found in DB (e.g., figures not yet synced), the screen shows an empty success state rather than an error.

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
| `FigureDetailViewModel.kt` | Switched from `EncouragementDao` to `EncouragementRepository`; queries by `figureId` |
| `AppModule.kt` | Updated Koin binding for `FigureDetailViewModel` to inject `EncouragementRepository` |
| `SharedModule.kt` | Updated `EncouragementRepositoryImpl` binding to include `FigureDao` |

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
