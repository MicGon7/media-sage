# MS-28: Switch News API to /top Endpoint

**Epic:** MS-1 (Server API Layer)
**Date completed:** 2026-04-20

## What was built

One-line change: switched `NewsApiService.getTopHeadlines()` from `/v1/news/all` to `/v1/news/top`.

## Key decisions & why

- **/top vs /all**: The `/all` endpoint returns every article published, sorted by time — a raw firehose. The `/top` endpoint returns editorially curated top stories by country. For an app matching theological quotes to meaningful news, curated stories are far more relevant.
- **Free tier compatible**: `/top` is available on all plans including free.
- **No DTO changes needed**: Both endpoints return the same article format.
- **Removed `sort` parameter**: `/top` has its own relevance sorting — no need to override with `published_at`.

## Gotchas

- Free tier still limits to 3 results per request regardless of endpoint.
