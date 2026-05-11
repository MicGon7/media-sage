# MS-158: Daily Briefing Card Freshness, Variety, and Tone Transitions

## Problem

The Home screen briefing card had three gaps:

1. **Stale across the day** — `loadBriefingCard()` only re-ran when the pinned figure or figures list changed. A tone transition from morning to evening (17:00) never triggered a new briefing fetch.
2. **Repeated scripture** — When an evening briefing was generated, the server had no context about the morning's scripture or reflection. Claude could independently land on the same verse or the same theological argument.
3. **No day-of-week variety** — The prompt had no date context, preventing Claude from using natural phrasing like "This Monday morning…" or varying reflections by day.

Pull-to-refresh was also disconnected from the briefing card entirely.

## What Changed

### Shared module

**`DailyReflectionDao`** — added `getAllForDay(figureId, epochDay)` to query all tone entries for a figure on a given calendar day. No schema change; no Room migration needed.

**`DailyReflectionRepositoryImpl.getOrFetch()`** — on a cache miss, queries today's existing entries before calling the API and passes three new fields to the server:
- `dayOfWeek` — computed from `kotlinx.datetime` (e.g. "Monday")
- `previousScriptures` — scripture references from today's earlier reflections
- `previousReflections` — full reflection text from today's earlier reflections

**`DailyReflectionRequestDto`** — added `dayOfWeek`, `previousScriptures`, `previousReflections` (all default to empty for backwards compatibility).

### Server module

**`DailyReflectionRequest` DTO** — same three fields added with defaults.

**`DailyReflectionService.generate()`** — accepts the new fields and passes them to `buildUserMessage()`.

**`buildSystemPrompt()`** — changed from "draw ONLY from these quotes" to a grounding model: quotes anchor the theological voice and direct Claude to specific source works; Claude may draw from its training knowledge of those works. This unlocks variety that a 5-quote ceiling would otherwise cap.

**`buildUserMessage()`** — added a `## Context` block with day/tone, and (when present) a previous-reflection summary that instructs Claude to avoid repeating the same argument while still allowing theme recurrence when headlines call for it.

### composeApp module

**`HomeViewModel`** — extracted `fetchAndUpdateBriefingCard(figureId)` as a shared suspend function used by both `loadBriefingCard()` and pull-to-refresh. Pull-to-refresh now calls `fetchAndUpdateBriefingCard()` after a successful headlines refresh — **without invalidating the cache**.

## Key Design Decisions

### No polling for tone transitions

An earlier proposal used `briefingSlotFlow()` — a flow that ticked every minute and emitted `epochDay_tone`, using `distinctUntilChanged()` to trigger a reload at 17:00. This was removed.

The real cases are covered without polling:
- **App reopened in the evening** — ViewModel re-initializes, `loadBriefingCard()` runs, tone is `evening`, cache miss → fresh fetch
- **Pull-to-refresh** — explicit user intent

The only case polling would add is "user stays in the app continuously across 17:00 without ever pulling to refresh" — too rare to justify a per-minute wakeup.

### Pull-to-refresh does not invalidate the briefing cache

The cache key is `${figureId}_${epochDay}_${tone}`. `getOrFetch()` returns the cached entry if it exists. Pull-to-refresh calls `fetchAndUpdateBriefingCard()`, which calls `getOrFetch()`:

- Same tone, same day → cache hit → no API call
- Tone changed or new day → cache miss → new API call

Invalidating on every pull would spend tokens for an identical result when the slot hasn't changed. Unlike headlines (where new articles arrive constantly), briefing reflections are keyed to a slot — once generated for a slot, they're correct until the slot changes.

### Quotes as grounding anchors, not a hard ceiling

The original prompt said "draw ONLY from these quotes." This limited Claude to the literal text of 5 pre-selected quotes, capping variety regardless of how rich the underlying source works are.

The new model: quotes establish the authentic theological voice and direct Claude to specific source works it knows deeply from training. Claude draws from those works freely; the quotes prevent it from inventing attribution or drifting to sources outside the figure's tradition. As the quote pool grows over time, more source works are represented and grounding deepens — but the ceiling on variety is removed.

### Theme restriction kept narrow

An earlier draft of the context instruction said "do NOT revisit the same theme." This was too aggressive — love, grace, and faithfulness are perennial precisely because headlines keep demanding them. Blocking a theme forces Claude to use a less-applicable one.

The final instruction: "avoid repeating the same argument" — Claude can revisit a theme if the headlines call for it, but must bring a fresh angle, a different application, or a deeper dimension.

### Both scripture and reflection passed as context

Passing only `previousScriptures` (verse references) blocks verse reuse but not argument reuse. Claude could arrive at the same theological angle using a different verse. Passing the full `previousReflections` text gives Claude the complete context of what argument was already made, so it can genuinely take a different direction.

## Testing

```bash
./gradlew :agent:test :server:test  # all pass
./gradlew detekt                    # clean
```

Manual smoke test:
- Pin a Reporter → morning briefing loads ✓
- Pull-to-refresh in the same morning session → no new API call (cache hit) ✓
- Change system time past 17:00, pull-to-refresh → evening briefing loads with different scripture ✓
- Unpin and repin same Reporter → cached briefing reused, no API call ✓
- Railway server logs confirm `dayOfWeek` and `previousScriptures` arrive in payload on genuine cache miss ✓
