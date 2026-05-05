# MS-133: Standardize repository Flow method naming

## What changed

Renamed all `Flow`-returning methods across the data layer to use the `observe` prefix, reserving `get` for `suspend` one-shot functions.

## Renames applied

**Repository interfaces (`domain/repository/`):**
- `HeadlineRepository.getHeadlines()` → `observeHeadlines()`
- `MatchRepository.getAllMatches()` → `observeAllMatches()`
- `QuoteRepository.getAllQuotes()` → `observeAllQuotes()`
- `QuoteRepository.getQuotesByFigure()` → `observeQuotesByFigure()`
- `FigureRepository.getAllFigures()` → `observeAllFigures()`
- `FigureRepository.getFiguresByCategory()` → `observeFiguresByCategory()`
- `EncouragementRepository.getByFigureId()` → `observeByFigureId()`

**DAOs (`data/local/dao/`):**
- `HeadlineDao.getAll()` → `observeAll()`
- `MatchDao.getAll()` → `observeAll()`
- `QuoteDao.getAll()` → `observeAll()`, `getByFigure()` → `observeByFigure()`
- `FigureDao.getAll()` → `observeAll()`, `getByCategory()` → `observeByCategory()`
- `EncouragementDao.getAll()` → `observeAll()`, `getBookmarked()` → `observeBookmarked()`, `getDistinctFigures()` → `observeDistinctFigures()`, `getByFigureName()` → `observeByFigureName()`, `getByFigureId()` → `observeByFigureId()`, `countByFigureName()` → `observeCountByFigureName()`

**Call sites updated:** repository implementations, ViewModels (`HomeViewModel`, `FiguresViewModel`, `FigureDetailViewModel`, `BookmarksViewModel`, `HistoryViewModel`), and all test fakes.

## Rule

- `observe*` — returns `Flow<T>`, stays alive and emits updates
- `get*` — `suspend fun`, resolves once and returns

## What stayed the same

- `MediaSageApi.getHeadlines()` — HTTP client, returns a suspended list (not a Flow); the `observe` prefix only applies to reactive streams
- Already-correct: `observePinnedFigureId()`, `observeIsBookmarked()`, `observeBookmarkState()`
