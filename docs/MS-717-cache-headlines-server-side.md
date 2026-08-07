# MS-717: Fetch and cache headlines server-side on a twice-daily schedule

## Bug

`GET /api/news/headlines` called `NewsApiClient.getTopHeadlines()` live on every request, so GNews
request volume scaled with client read volume against a 100 requests/day free-tier cap. Separately,
`getTopHeadlines()` sent GNews' `/top-headlines` category filter under the query parameter name
`topic`, but GNews documents that parameter as `category` — so category-scoped fetching silently
returned unfiltered results.

## Fix

Added `HeadlineFetchService.fetchAndStoreAll()`, which fetches all seven relevant GNews categories
(`general`, `world`, `nation`, `business`, `technology`, `science`, `health`) and stores each via
the new `HeadlineRepository.replaceCategory()` (delete-then-insert against the new `HeadlineTable`).
One category's failure is caught and recorded in the returned `FetchSummary.failed` without
aborting the others. `HeadlineFetchScheduler.launchHeadlineFetchLoop()` fetches once immediately on
startup, then loops on `millisUntilNextFetchWindow()` — the same local 5pm/midnight boundary
`BriefingToneScheduler` (composeApp) already uses. `NewsRoutes`'s `/headlines` now reads from
`HeadlineRepository.getStored()` instead of calling `NewsApiClient` directly; `/search` is
unchanged since ad-hoc search terms aren't cacheable the same way. Also fixed the `topic`→`category`
GNews parameter name.

## Why an explicit `CoroutineScope`, not Ktor's application-scoped one

`Application.module()` starts the fetch loop with `CoroutineScope(Dispatchers.IO).launchHeadlineFetchLoop(...)`
rather than a Ktor-provided application-lifetime scope. `ArticleScraperService`'s existing
fire-and-forget work in this module follows the same pattern, and it avoids taking on a Ktor
application-scope API whose cancellation-on-shutdown behavior wasn't already verified elsewhere in
this codebase. The tradeoff: this scope is never explicitly cancelled, so the loop runs for the
JVM process's lifetime — acceptable here since the server process itself is the unit of deployment
lifetime on Railway, but worth carrying forward if a future feature needs the loop to stop before
process exit.

## Exposed gotcha: `deleteWhere`'s lambda shape differs from `Query.where`

`Query.where { Table.column eq value }` and `Table.deleteWhere { Table.column eq value }` look like
the same idiom but are not: `where` takes a `SqlExpressionBuilder.() -> Op<Boolean>` (pure receiver
lambda, so `eq` — a member of `ISqlExpressionBuilder` — resolves against the implicit receiver
directly), while `deleteWhere` takes `T.(ISqlExpressionBuilder) -> Op<Boolean>` (the *table* is the
receiver; the builder is passed as an explicit parameter). Writing `Table.deleteWhere { Table.column eq value }`
fails to compile with `Unresolved reference 'eq'`, because `eq`'s dispatch receiver
(`ISqlExpressionBuilder`) is never in implicit scope. The fix is to bring the builder parameter into
scope explicitly: `Table.deleteWhere { builder -> builder.run { column eq value } }`. This is the
detail to carry forward: **any future `deleteWhere`/`deleteIgnoreWhere` call needs the builder
parameter run/with-wrapped**, unlike every `select`/`where` call in this codebase.

## How to test

Start the server, confirm `/api/news/headlines?category=world` returns articles immediately (the
startup fetch populates the cache before the first request), then stop the GNews-backed
`NewsApiClient` from the network and re-request the same endpoint — it should still return the
cached articles with no outbound GNews call.
