# MS-86: Replace TheNewsAPI with GNews

## What changed

Replaced TheNewsAPI (`api.thenewsapi.com`) with GNews (`gnews.io/api/v4`) as the news data source.

**Why:** TheNewsAPI free tier returns only 3 articles per request. GNews free tier returns up to 10, which gives Claude more headlines to match against and makes the app feel substantive.

## GNews API shape

GNews uses different field names from TheNewsAPI:

| Concept | TheNewsAPI | GNews |
|---|---|---|
| Auth param | `api_token` | `token` |
| Language | (query param) | `lang` |
| Article count | `limit` | `max` |
| Image field | `image_url` | `image` |
| Source | `source` (string) | `source.name` (nested object) |
| Published date | `published_at` | `publishedAt` |
| Total count | `meta.found` | `totalArticles` |

The `NewsArticle` domain model returned to clients was unchanged — only the internal GNews DTOs changed.

## Language filtering

`lang=en` and `country=us` are passed on every request. Confirmed via Postman: GNews honors `lang=en` and returns English-only articles. The free tier has a 12-hour delay on real-time data, which is acceptable for this use case.

A non-English article seen during development was traced to Railway still running the old TheNewsAPI code — not a GNews issue.

## UUID generation

GNews articles have no stable ID on the free tier (the `id` field appears only on paid plans). UUID is derived deterministically from the article URL:

```kotlin
UUID.nameUUIDFromBytes(url.toByteArray()).toString()
```

This is the same pattern TheNewsAPI used, so caching by UUID remains stable.

## Sports/entertainment filter

GNews does not support topic exclusion on the free tier. The filter is applied post-fetch by checking the article title for keywords:

```kotlin
private val EXCLUDED_TOPICS = setOf("sports", "entertainment")

.filter { article -> EXCLUDED_TOPICS.none { topic -> article.title.contains(topic, ignoreCase = true) } }
```

## Railway deployment note

After merging, add `GNEWS_API_KEY` to Railway environment variables and click **Apply Changes**. The old `NEWS_API_KEY` variable is no longer read. Also ensure Railway's deployment branch is set to `main` (not a stale feature branch).
