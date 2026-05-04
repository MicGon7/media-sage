# MS-128: Refactor FigureDetailViewModel — Replace EncouragementDao with EncouragementRepository

## What changed

`FigureDetailViewModel` was directly injecting `EncouragementDao`, bypassing the repository layer. This ticket wires it through `EncouragementRepository` instead.

### Files modified

| File | Change |
|------|--------|
| `shared/.../domain/repository/EncouragementRepository.kt` | Added `getByFigureName(figureName: String): Flow<List<Encouragement>>` |
| `shared/.../data/repository/EncouragementRepositoryImpl.kt` | Implemented it via `encouragementDao.getByFigureName().map { entities -> entities.map { it.toDomain() } }` |
| `composeApp/.../feature/figures/FigureDetailViewModel.kt` | Replaced `EncouragementDao` dependency with `EncouragementRepository` |
| `composeApp/.../di/AppModule.kt` | Updated Koin binding to inject `EncouragementRepository` instead of `EncouragementDao` |

## Why it matters

ViewModels should only depend on repository interfaces, not DAOs. DAOs are implementation details of the data layer — exposing them to the UI layer breaks the layering contract and makes the ViewModel harder to test (you'd need a real Room database instead of a simple fake).

## Pattern

When a ViewModel needs a query that isn't yet on the repository interface:
1. Add the method to the interface in `domain/repository/`
2. Implement it in `data/repository/` using the DAO + mapper
3. Update the ViewModel to call the repository method
4. Update the Koin module to inject the repository type (not the DAO)

The `map` operator from `kotlinx.coroutines.flow` is the right tool for transforming a `Flow<List<Entity>>` into a `Flow<List<DomainModel>>`.
