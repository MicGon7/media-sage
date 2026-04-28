# MS-79: Fix NullPointerException in WikimediaService Portrait Cache

## Problem

`WikimediaService.getPortraitUrl` crashed with a `NullPointerException` when a Wikimedia lookup returned no portrait. The cache was declared as `ConcurrentHashMap<String, String?>` with the intent of storing null to represent "no portrait found", but `ConcurrentHashMap` forbids null values — any `put(key, null)` throws immediately.

Stack trace:
```
java.lang.NullPointerException: null
    at java.base/java.util.concurrent.ConcurrentHashMap.putVal(ConcurrentHashMap.java:1011)
    at com.mediasage.server.service.WikimediaService.getPortraitUrl(WikimediaService.kt:25)
```

This caused a 500 response to every client requesting a portrait for a figure with no Wikipedia image, which surfaced as "Something went wrong" in the UI.

## Fix

Changed the cache to `ConcurrentHashMap<String, String>` (non-nullable value type) and introduced a private sentinel constant `NO_PORTRAIT = ""` to represent a confirmed miss. Reading from the cache converts the sentinel back to null before returning to the caller.

```kotlin
private val cache = ConcurrentHashMap<String, String>()

suspend fun getPortraitUrl(figureName: String): String? {
    val cached = cache[figureName]
    if (cached != null) return if (cached == NO_PORTRAIT) null else cached
    val url = fetchPortraitUrl(figureName)
    cache[figureName] = url ?: NO_PORTRAIT
    return url
}
```

The sentinel approach preserves the original intent: figures with no portrait are cached so repeated lookups don't hit the network.

## Pattern

`ConcurrentHashMap` is the standard JVM concurrent map but **does not allow null keys or values**. When you need to cache nullable results:

- **Option A (used here):** Store a sentinel non-null value and convert on read.
- **Option B:** Use two separate structures — a `ConcurrentHashMap<String, String>` for hits and a `ConcurrentHashSet<String>` for confirmed misses.
- **Option C:** Use `java.util.Collections.synchronizedMap(HashMap())` which allows nulls, but gives up `ConcurrentHashMap`'s lock-striped write performance.

Option A is the simplest for a low-cardinality cache like this one.

## Tests Added

`WikimediaServiceTest` covers:
- Returns URL when thumbnail is present in the Wikimedia response
- Returns null (no crash) when no thumbnail is present
- Does not throw after caching a null result (regression test for the NPE)
- Caches null results — second lookup for the same figure makes no HTTP request
- Caches valid URLs — second lookup makes no HTTP request
