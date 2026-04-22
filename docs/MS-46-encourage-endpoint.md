# MS-46: Encourage Endpoint

## What Changed
Added a new server endpoint `POST /api/analysis/encourage` that accepts a headline and returns a complete encouragement response from Claude. The client only sends the headline — the server handles all matching logic.

## Endpoint

### POST /api/analysis/encourage

**Request:**
- `headlineTitle` (required) — the news headline
- `locale` (optional, default "en") — response language
- `articleText` (optional, nullable) — full article text for Claude to summarize

**Response:**
- `summary` — 2-3 sentence article lede (null when no articleText provided)
- `quoteText` — encouraging quote from a Christian figure
- `figureName` — name of the figure
- `figureRole` — short role descriptor
- `scriptureReference` — e.g. "Romans 8:28"
- `scriptureText` — the scripture passage text
- `explanation` — 2-3 sentence connection explanation
- `connectionThemes` — list of thematic connections
- `matchTheme` — short theme label
- `tone` — COMFORT, EXHORTATION, or CORRECTION

## Tone (Parakaleo)

Claude discerns which tone fits the headline, inspired by the Greek parakaleo:

| Tone | When | Example |
|------|------|---------|
| COMFORT | Suffering, loss, disaster | Earthquake headline → Augustine quote on suffering |
| EXHORTATION | Opportunity, community, faithfulness | Community gardens → Calvin on stewardship |
| CORRECTION | Moral drift, corruption, injustice | Corporate violations → MLK on justice |

## Claude Prompt Design

The system prompt instructs Claude to:
1. Discern the appropriate tone
2. Summarize the article if text is provided (2-3 sentence lede)
3. Select a real, verified quote — no fabricated quotes
4. Find a relevant scripture passage
5. Explain the connection
6. Respond in the specified locale language

## Old Endpoint

`POST /api/analysis/match` is deprecated but still functional. It required the client to send candidate quotes — the new endpoint removes that requirement.

## Files Changed

| File | Change |
|------|--------|
| `server/routes/AnalysisRoutes.kt` | Added encourage route, split into separate route functions for detekt |
| `server/service/ClaudeApiService.kt` | Added encourageHeadline(), EncourageResult, EncourageTone, new system prompt |
