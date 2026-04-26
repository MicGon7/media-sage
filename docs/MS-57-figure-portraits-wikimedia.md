# MS-57: Dynamic Figure Portraits via Wikimedia Commons

## What Was Done

After Claude returns a figure name in the encourage response, the server looks up a portrait thumbnail from the Wikipedia API and includes the URL in the response. The client displays the portrait as a circular image in the quote attribution on the match detail screen, falling back to the initials placeholder when no image is available.

## Implementation

### Server — WikimediaService
New `WikimediaService` calls the Wikipedia API using the figure name:
```
https://en.wikipedia.org/w/api.php?action=query&titles={name}&prop=pageimages&pithumbsize=300&format=json
```
The response page ID is dynamic, so parsing uses `JsonObject` directly rather than a typed data class — the first entry in the `pages` map is always the result.

Results are cached in a `ConcurrentHashMap<String, String?>`. Null values are explicitly cached to prevent repeated failed lookups for figures with no portrait.

### Route Change
`AnalysisRoutes.kt` calls `wikimediaService.getPortraitUrl(result.figureName)` after Claude responds, then copies the URL into the result:
```kotlin
call.respond(result.copy(figureImageUrl = figureImageUrl))
```

### Client
`figureImageUrl: String?` added to `EncourageResultDto`, `Encouragement` domain model, `EncouragementEntity`, and `EncouragementState.Loaded`. Room schema bumped to version 4.

The quote attribution in `MatchScreen` shows `AsyncImage` clipped to a circle when `figureImageUrl` is non-null, with `Icons.Default.Person` as the error/fallback painter. Falls back to `FigurePlaceholder` (initials circle) when null.

## Key Learnings

### Wikimedia API — Dynamic Page Keys
The Wikipedia API wraps results in a `pages` map keyed by an internal page ID (e.g. `"3002"`, or `"-1"` for missing pages). You can't deserialize this into a typed data class — use `JsonObject` and take `values.firstOrNull()`:
```kotlin
val pages = json["query"]?.jsonObject?.get("pages")?.jsonObject
val page = pages?.values?.firstOrNull()?.jsonObject
val url = page?.get("thumbnail")?.jsonObject?.get("source")?.jsonPrimitive?.content
```

### Cache Null Values Explicitly
`ConcurrentHashMap.containsKey()` distinguishes between "key not present" and "key maps to null". Use `containsKey` to gate the cache check so confirmed-no-image results are respected and not retried on every request.

### Image URL Persistence is Free
Because `EncouragementEntity` already caches the full encouragement in Room (MS-63), adding `figureImageUrl` as a nullable column means the portrait URL is persisted with zero extra work. The Wikimedia lookup is a one-time cost per figure name on the server, and a one-time cost per article on the client.

### Always Restart the Server
Server code changes require a full server restart — the running process doesn't hot-reload. When a feature appears broken despite correct client code, verify the server is running the new build before debugging further.
