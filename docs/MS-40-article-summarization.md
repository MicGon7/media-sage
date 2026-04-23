# MS-40: Article Scraping and Summarization

## What Changed
Added server-side article scraping with Jsoup so Claude can summarize articles before matching. Refactored the Match screen to progressive loading — headline data shows instantly, encouragement loads asynchronously.

## Architecture

### Server: Article Scraping

**ArticleScraperService** (`server/service/ArticleScraperService.kt`):
- Jsoup scrapes article text from a URL
- In-memory cache (`ConcurrentHashMap`) stores scraped text
- `preScrape(urls)` kicks off background scraping for multiple URLs
- `getArticleText(url)` reads from cache or scrapes on demand
- 15-second timeout, max 5000 chars, custom User-Agent
- Strips non-content elements (nav, footer, ads) and tries common article selectors

**Pre-fetch flow:**
1. Client requests `/api/news/headlines`
2. Server returns headlines AND triggers `preScrape()` for all article URLs
3. By the time user taps a headline, article text is already cached
4. Encourage endpoint reads from cache — no scraping delay

### Server: Claude Prompt Updates
- Figures restricted to professing Christians who lived before 1980
- No philosophers or ethicists outside the faith
- Server HttpClient timeout increased to 60s (Claude can take 10-30s)

### Client: Progressive Loading

**Before:** Full loading screen → all data at once
**After:** Headline data instantly → encouragement loads below

`MatchContract.UiState.Success` now contains nested `EncouragementState`:
```
Success (immediate from Room)
├── headlineTitle, headlineSource, headlineImageUrl
└── EncouragementState
    ├── Loading (spinner below headline)
    ├── Loaded (quote card, scripture, summary)
    └── Error (retry button, headline stays visible)
```

### Client: EncouragementRepository

Extracted API call from ViewModel into proper repository pattern:
```
ViewModel → EncouragementRepository → MediaSageApi → DTO → Domain (Encouragement)
```
ViewModel no longer imports or handles DTOs directly.

## New Domain Model

`Encouragement` (`shared/domain/model/Encouragement.kt`):
- summary, quoteText, figureName, figureRole
- scriptureReference, scriptureText
- explanation, connectionThemes, matchTheme, tone

## Files Changed/Created

### New
| File | Purpose |
|------|---------|
| `server/service/ArticleScraperService.kt` | Jsoup scraping with cache |
| `shared/domain/model/Encouragement.kt` | Domain model for Claude response |
| `shared/domain/repository/EncouragementRepository.kt` | Repository interface |
| `shared/data/repository/EncouragementRepositoryImpl.kt` | API call + DTO mapping |

### Modified
| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added Jsoup 1.18.3 |
| `server/build.gradle.kts` | Jsoup dependency |
| `server/di/ServerModule.kt` | ArticleScraperService in Koin, HttpClient timeout 60s |
| `server/routes/AnalysisRoutes.kt` | articleUrl param, scraping integration |
| `server/routes/NewsRoutes.kt` | Pre-scrape on headline fetch |
| `server/service/ClaudeApiService.kt` | Christian-only figures, pre-1980 |
| `shared/di/SharedModule.kt` | EncouragementRepository in Koin |
| `shared/data/mapper/EntityMappers.kt` | EncourageResultDto.toDomain() |
| `shared/data/remote/ApiDtos.kt` | articleUrl replaces articleText |
| `shared/data/remote/HttpClientFactory.kt` | Client timeout 60s |
| `composeApp/feature/match/MatchContract.kt` | Nested EncouragementState |
| `composeApp/feature/match/MatchViewModel.kt` | Progressive loading, uses repository |
| `composeApp/feature/match/MatchScreen.kt` | Two-phase rendering |
