# MS-8: Scripture API Integration Service

**Epic:** MS-1 (Server API Layer)
**Date completed:** 2026-04-19

## What was built

Server-side Scripture API integration using API.Bible for fetching Bible verses and passages.

### Service layer
- **ScriptureApiDtos.kt** — Response DTOs for search results (`ScriptureSearchResponse`, `ScriptureVerse`), passages (`ScripturePassageResponse`, `ScripturePassage`), and Bible versions (`BiblesResponse`, `BibleVersion`)
- **ScriptureApiService.kt** — HTTP client service with `searchVerses()` and `getPassage()` methods

### Routes
- `GET /api/scripture/search?query=hope` — search verses by keyword
- `GET /api/scripture/passage/{passageId}` — get a specific passage (e.g., `JHN.3.16`)

### DI & Error handling
- `ScriptureApiService` added to `serverModule` Koin config with `SCRIPTURE_API_KEY`
- `ScriptureApiException` handled by StatusPages

## Key decisions & why

- **API.Bible over other Bible APIs**: Has 1500+ Bible versions, unified formatting, free for non-commercial use. Matches our plan.
- **ASV as default Bible**: American Standard Version (public domain). Can be changed per request via `bibleId` parameter.
- **`api-key` header auth**: Unlike TheNewsAPI (query parameter) and Claude (custom header), API.Bible uses `api-key` as the header name. Each external API has its own auth pattern.
- **Text content type**: Request `content-type=text` from API.Bible to get plain text instead of HTML. Easier to display and process.

## Concepts learned

- **API.Bible verse IDs**: Use format `BOOK.CHAPTER.VERSE` (e.g., `ROM.8.24`, `JHN.3.16`). Book IDs are 3-letter codes.
- **Three external APIs, three patterns**: Claude (POST, custom headers), TheNewsAPI (GET, query param token), API.Bible (GET, header token). The service pattern we established abstracts these differences.
- **Rate limits**: API.Bible allows 5,000 queries/day and 500 consecutive verses per request. Sufficient for development.

## Gotchas

- API.Bible returns HTML content by default — must explicitly request `content-type=text` parameter.
- The `SCRIPTURE_API_KEY` env var must be set. Register at scripture.api.bible to get a key.
- All three server API services (Claude, News, Scripture) are now complete — the server module is the single gateway for all external API calls.
