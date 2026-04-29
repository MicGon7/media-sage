# MS-56: SSE Streaming for Real-Time Encouragement Display

## What Changed

Replaced the full-screen Lottie loading animation on the headline detail screen with a progressive streaming UX. Headline data appears instantly from Room, skeleton loaders fill the encouragement section while Claude streams, then each section transitions from skeleton to content as Claude's response arrives field by field.

> **Known gap:** Character-by-character streaming within each field is not yet visible — sections transition from skeleton to full text rather than building up gradually. Root cause TBD (likely OkHttp response buffering or the `---FIELD---` delimiter approach batching field values). Tracked for follow-up.

## Architecture

### Server-side SSE (`:server`)

**No Ktor SSE plugin.** `ktor-server-sse` plugin's `respondSse` extension is only accessible inside its own DSL, not in `post { }` handlers. Use `call.respondBytesWriter(ContentType.Text.EventStream)` to write SSE frames manually as a `ByteWriteChannel`.

```kotlin
call.applySseHeaders()
call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
    claudeService.encourageHeadlineStream(...).collect { (fieldName, text) ->
        sendSseEvent("delta", json.encodeToString(SseDeltaPayload(fieldName, text)))
    }
    sendSseEvent("done", "")
    wikimediaService.getPortraitUrl(figureName)?.let { sendSseEvent("portrait", it) }
}
```

Three SSE event types:
- `delta` — a chunk of text for a named field (`{"field":"QUOTE","text":"..."}`)
- `done` — Claude stream is complete
- `portrait` — Wikimedia portrait URL, fetched after stream ends

### Claude Streaming Format

Claude outputs 10 fields separated by `---FIELD---` in a fixed order, no labels or JSON:

```
matchTheme---FIELD---tone---FIELD---summary---FIELD---quoteText---FIELD---figureName---FIELD---figureRole---FIELD---scriptureReference---FIELD---scriptureText---FIELD---explanation---FIELD---connectionThemes
```

The server reads Claude's `content_block_delta` SSE events, accumulates text in a buffer, and emits named field chunks to the client as delimiters are found. The buffer holds back `FIELD_DELIMITER.length` (13) characters to avoid emitting partial delimiters as content.

### Client-side SSE (`:shared`)

**No Ktor SSE client.** Parse manually using `preparePost().execute { response.bodyAsChannel().readUTF8Line() }`.

**`channelFlow` required, not `flow`.** The `execute { }` lambda is not a `FlowCollector` scope, so `emit()` from an outer `flow { }` is inaccessible. `channelFlow` provides `ProducerScope` with `send()` accessible from nested coroutine contexts.

**Streaming timeout.** Default `requestTimeoutMillis = 60_000` kills long-lived streams. A lazy-derived `streamingClient` overrides this:
```kotlin
private val streamingClient: HttpClient by lazy {
    httpClient.config { install(HttpTimeout) { requestTimeoutMillis = Long.MAX_VALUE } }
}
```

### Domain Events

`StreamEvent` sealed class in `shared/domain/model/StreamEvent.kt`:
- `FieldDelta(field: StreamField, text: String)` — incremental text for a field
- `Portrait(url: String)` — Wikimedia portrait URL
- `Cached(encouragement: Encouragement)` — cache hit, skip streaming
- `Done` — stream complete, triggers Room save in repository

### MVI State

`EncouragementState.Streaming` holds all 10 field string buffers plus `activeField: StreamField`. The ViewModel appends each `FieldDelta` to the correct buffer via `withDelta()`. `activeField.ordinal` drives the UI: sections with ordinal ≤ `activeField.ordinal` show live text, sections above show skeleton loaders.

On `Done`, the ViewModel transitions to `EncouragementState.Loaded`. On cache hit (`Cached`), it transitions directly to `Loaded` — no skeletons at all.

`EncouragementState.Loading` was removed entirely. The ViewModel starts with `EncouragementState.Streaming()` so skeletons appear immediately when the screen opens, before the first Claude token.

### Detekt Compliance

The streaming methods required significant decomposition to pass `LongMethod`, `CyclomaticComplexMethod`, and `NestedBlockDepth` rules.

`LoopWithTooManyJumpStatements` was the trickiest: a `while` loop with both `break` and `continue` violates this rule. Fix: replace both with a `var streamDone = false` flag and conditional logic — no jump statements.

## Files Changed

- `server/.../service/ClaudeApiDtos.kt` — `stream` field on `ClaudeRequest`, `ClaudeStreamDelta`, `ClaudeTextDelta`
- `server/.../service/ClaudeApiService.kt` — `encourageHeadlineStream()`, `ENCOURAGE_STREAM_SYSTEM_PROMPT`, decomposed helpers
- `server/.../routes/AnalysisRoutes.kt` — `/encourage/stream` SSE route, `SseDeltaPayload`, SSE helpers
- `shared/.../domain/model/StreamEvent.kt` — new file: `StreamField` enum, `StreamEvent` sealed class
- `shared/.../remote/MediaSageApi.kt` — added `encourageStream()` to interface
- `shared/.../remote/MediaSageApiImpl.kt` — `encourageStream()` with manual SSE parsing, `streamingClient`
- `shared/.../repository/EncouragementRepository.kt` — added `streamEncouragement()` to interface
- `shared/.../repository/EncouragementRepositoryImpl.kt` — cache check → stream → Room save
- `composeApp/.../headlinedetail/HeadlineDetailContract.kt` — added `EncouragementState.Streaming`, removed `Loading`
- `composeApp/.../headlinedetail/HeadlineDetailViewModel.kt` — rewrote `loadMatch()` for streaming
- `composeApp/.../headlinedetail/HeadlineDetailScreen.kt` — added `ShimmerSkeleton`, `EncouragementStreaming`, removed Lottie
