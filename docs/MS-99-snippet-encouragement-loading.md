# MS-99: Use article snippet to reduce encouragement loading time

## What changed

The GNews API returns a `snippet` field (1–3 sentence article excerpt) alongside each headline. We now persist this snippet in Room and pass it to the server when requesting encouragement.

The server's `/api/analysis/encourage` endpoint now prefers `articleSnippet` over scraping the full article URL. Scraping only runs when no snippet is present and an `articleUrl` is provided — which means the ~9 second scrape delay is eliminated for all articles that come with a snippet (the vast majority).

## Why

Article scraping was the dominant latency driver on the headline detail screen. Claude only needs the snippet to write an accurate 1–2 sentence summary; it doesn't require the full article body.

## Data flow

```
GNews API response
  └── NewsArticleDto.snippet
        └── HeadlineEntity.snippet (Room v9)
              └── Headline.snippet (domain)
                    └── HeadlineDetailViewModel → getEncouragement(articleSnippet)
                          └── EncourageRequestDto.articleSnippet
                                └── POST /api/analysis/encourage
                                      └── articleSnippet ?? scrape(articleUrl) → Claude
```

## Room migration

`HeadlineEntity` gained a nullable `snippet` column. Database bumped to version 9. `fallbackToDestructiveMigration` handles the upgrade automatically (no manual SQL needed for this project).

## Key files

| File | Change |
|---|---|
| `domain/model/Headline.kt` | Added `snippet: String?` |
| `data/local/entity/HeadlineEntity.kt` | Added `snippet: String?` |
| `data/mapper/EntityMappers.kt` | Map snippet through DTO → entity → domain |
| `data/remote/ApiDtos.kt` | `EncourageRequestDto.articleSnippet: String?` |
| `domain/repository/EncouragementRepository.kt` | `articleSnippet` param on `getEncouragement` |
| `data/repository/EncouragementRepositoryImpl.kt` | Pass snippet in request DTO |
| `feature/headlinedetail/HeadlineDetailViewModel.kt` | Pass `headline.snippet` |
| `server/routes/AnalysisRoutes.kt` | `articleSnippet` in server DTO; prefer over scraping |
| `data/local/db/MediaSageDatabase.kt` | Bumped to version 9 |
