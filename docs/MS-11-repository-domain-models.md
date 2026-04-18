# MS-11: Repository Pattern & Domain Models

**Epic:** MS-2 (Shared Data Layer)
**Date completed:** 2026-04-18

## What was built

Clean architecture data layer in the shared KMP module:

### Domain models (`domain/model/`)
- `Figure` — with `FigureCategory` enum (THEOLOGIAN, MYSTIC, MODERN, BIBLICAL)
- `Quote` — themes as `List<String>` (clean type, not comma-separated)
- `Headline` — cached news headlines
- `Match` — AI-matched headline-to-quote pairs with connectionThemes as `List<String>`

### Repository interfaces (`domain/repository/`)
- `FigureRepository` — getAllFigures, getFiguresByCategory, getFigureById
- `QuoteRepository` — getAllQuotes, getQuotesByFigure, getQuoteById
- `HeadlineRepository` — getHeadlines (Flow), refreshHeadlines, clearOldHeadlines
- `MatchRepository` — getAllMatches, getMatchForHeadline, requestMatch

### Repository implementations (`data/repository/`)
- All implementations read from Room DAOs and map entities to domain models
- `HeadlineRepositoryImpl.refreshHeadlines()` — TODO for remote API (MS-10)
- `MatchRepositoryImpl.requestMatch()` — cache-first, remote fallback TODO (MS-10)

### Mappers (`data/mapper/EntityMappers.kt`)
- Extension functions: `toDomain()` and `toEntity()` for all four types
- Handle comma-separated string ↔ List<String> conversion for themes

### DI (`di/SharedModule.kt`)
- Koin module binding repository interfaces to implementations

## Key decisions & why

- **Domain models separate from entities**: Entities have Room annotations and store themes as strings. Domain models are clean data classes with proper types (enums, lists). The UI never sees Room entities.
- **Extension function mappers**: `entity.toDomain()` is more readable than `EntityMapper.toDomain(entity)`. Keeps mapping logic close to the types without polluting them.
- **Repository interfaces in domain layer**: No Room or Ktor dependencies. The domain layer only knows about domain models and Flows. Implementations in the data layer handle the actual data sources.
- **TODO stubs for remote API**: `refreshHeadlines()` and `requestMatch()` have TODO comments pointing to MS-10. The local-only path works now; remote will be layered in.
- **FigureCategory enum with fromString()**: Converts the raw string stored in Room to a type-safe enum. Defaults to THEOLOGIAN for unknown values — defensive but won't crash.

## Concepts learned

- **Clean architecture layers**: domain (models + repository interfaces) → data (implementations + mappers + DAOs) → presentation (ViewModels). Each layer only depends inward.
- **Flow mapping**: `dao.getAll().map { entities -> entities.map { it.toDomain() } }` — the outer `map` transforms the Flow emission, the inner `map` transforms each entity in the list.
- **Koin interface binding**: `single<FigureRepository> { FigureRepositoryImpl(get()) }` — binds the interface to the implementation. `get()` resolves the DAO dependency from the Koin graph.

## Gotchas

- Repository tests with mocked DAOs deferred — mocking in KMP requires additional setup (no Mockito in commonTest). Will add when remote APIs are wired in MS-10.
