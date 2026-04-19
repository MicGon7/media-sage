# MS-7: News API Integration Service

**Epic:** MS-1 (Server API Layer)
**Date completed:** 2026-04-19

## What was built

Server-side News API integration using TheNewsAPI (thenewsapi.com) for fetching news headlines.

### Service layer
- **NewsApiDtos.kt** — Response DTOs (`NewsApiResponse`, `NewsApiMeta`, `NewsArticle`) matching TheNewsAPI's JSON structure
- **NewsApiService.kt** — HTTP client service with `getTopHeadlines()` and `searchNews()` methods

### Routes
- `GET /api/news/headlines` — top headlines with optional `locale`, `language`, `limit` params
- `GET /api/news/search` — search news with required `query` param

### DI & Error handling
- `NewsApiService` added to `serverModule` Koin config with `NEWS_API_KEY`
- `NewsApiException` handled by StatusPages (alongside `ClaudeApiException`)

## Key decisions & why

- **TheNewsAPI `/v1/news/all` endpoint**: Used for both headlines and search rather than the `/v1/news/headlines` endpoint. The `all` endpoint supports search, sorting, and pagination — more flexible for our needs.
- **API token as query parameter**: TheNewsAPI uses `api_token` as a GET parameter, not a header. Different from Claude's header-based auth.
- **Separate DTOs from domain models**: `NewsArticle` DTO has `uuid` and `published_at` as strings. The repository layer (MS-11) will convert these to domain `Headline` with `Long` IDs and epoch millis.

## Concepts learned

- **Ktor client GET with parameters**: `parameter("key", "value")` adds query parameters to the URL automatically — no manual URL building.
- **`@SerialName` for field mapping**: TheNewsAPI uses `image_url`, `published_at` — mapped to `imageUrl`, `publishedAt` in Kotlin.
- **StatusPages for service-specific exceptions**: Each external API service has its own exception type. StatusPages catches them and returns the appropriate HTTP status code to the client.

## Gotchas

- TheNewsAPI free tier has rate limits — monitor usage during development.
- `NEWS_API_KEY` must be set in environment. Register at thenewsapi.com to get a key.
