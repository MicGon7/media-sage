# MS-103: Update Encourage Flow — Claude Selects from Stored Figures and Quotes

## What changed

Before this ticket, the `/encourage` endpoint called Claude and let it freely generate any quote from any figure, completely bypassing the 707 seeded, verified quotes in the database.

After this ticket, the encourage flow is:

1. `AnalysisRoutes` fetches all quotes + figures from the DB via `ServerDatabase.fetchQuoteCandidates()`
2. `ClaudeApiService.sampleCandidates()` filters out recently used figures, shuffles, caps at max 2 quotes per figure, and takes the top 20 as a candidate pool
3. Claude receives the candidate pool and returns a `selectedQuoteId` — it cannot invent new quotes or figures
4. The service validates the returned ID against the pool. On failure, it retries once with `strictIds = true` (the prompt includes the explicit list of valid IDs)
5. `EncourageResult` is populated from DB data, not Claude's free-form text

## Key types

**`SelectionResult`** — what Claude returns: `selectedQuoteId`, `summary`, `scriptureReference`, `scriptureText`, `explanation`, `connectionThemes`, `matchTheme`, `tone`

**`EncourageResult`** — what the API responds with: `quoteText`, `quoteSource`, and figure details come from the DB row matched by `selectedQuoteId`; scripture, explanation, and themes come from Claude's selection

`quoteSource` (e.g. `"The Freedom of a Christian (1520)"`) is included so clients can display attribution alongside the quote.

## Token cost trade-off and the path forward

Sending 20 full candidate quotes to Claude adds ~3,000 input tokens per request (~$0.009 at Sonnet pricing). For a demo this is negligible, but the pattern doesn't scale.

The right fix (tracked as MS-104) is **server-side pre-filtering**: score quotes by theme overlap with the headline before calling Claude, then send only the top 5 most relevant candidates. This reduces tokens by ~75% and improves selection quality — Claude gets relevant candidates, not random ones.

**This is a deliberate design step.** First prove correctness (Claude selects from real verified quotes), then optimize token usage. Doing both at once would have mixed two concerns.

## Figure diversity

`ClaudeApiService` holds a `recentFigures` LinkedHashMap (bounded at 10 entries, insertion-ordered). After each selection, the figure name is added. Sampling skips these figures so consecutive requests don't repeat the same voice.

## Naming conflict

`ClaudeApiService` already had a `QuoteCandidate` type for the deprecated `/match` route. `ServerDatabase` also defines `QuoteCandidate`. Resolved with an import alias:

```kotlin
import com.mediasage.server.db.QuoteCandidate as DbQuoteCandidate
```

## Detekt: LongMethod

`encourageHeadline` and `buildEncourageMessage` each exceeded the 30-line limit after the candidate-pool logic was added. Fixed by:
- Extracting `resolveSelection()` — a private suspend helper that handles the pool lookup + retry
- Collapsing a multi-line string concatenation in `buildEncourageMessage` into a single line via a local `val`

## Pre-existing test fix

`FigureRoutesTest.setup()` was failing on main with `UNIQUE constraint failed: figures.name`. Cause: Exposed's connection pool reuses the SQLite `:memory:` connection across test classes, so the figures table retained data from a previous test class. Fix: `FigureTable.deleteAll()` before inserting seed rows in `@BeforeTest`.

## EncourageRequest: recentFigures removed

The old `EncourageRequest` had a `recentFigures: List<String>` field the client sent to steer figure diversity. Since the server now manages diversity internally via the `recentFigures` sliding window, this field was removed. Clients still sending `recentFigures` in JSON are unaffected — Ktor ignores unknown fields by default.
