# App Flow — End-to-End

How The Media Sage app works from launch to displayed match, including all external API roles.

```mermaid
sequenceDiagram
    actor User
    participant App as App<br/>(composeApp + shared)
    participant Room as Room DB<br/>(on-device cache)
    participant Server as App API<br/>(Railway · :appServer · port 8080)
    participant NewsAPI as The News API
    participant Claude as Anthropic Claude API<br/>(via Fuelix proxy)
    participant Scripture as Scripture API

    Note over User,Room: App launch — cache-first

    User->>App: Open app
    App->>Room: Observe headlines (Flow)
    Room-->>App: Emit cached headlines immediately
    App->>Server: GET /news/headlines
    Server->>NewsAPI: Fetch top headlines
    NewsAPI-->>Server: Headline list
    Server-->>App: Headline DTOs
    App->>Room: Upsert headlines
    Room-->>App: Emit updated headlines

    Note over User,Claude: Match on tap — on-demand

    User->>App: Tap headline
    App->>Room: Check cache by articleUrl
    alt Cache hit
        Room-->>App: Return cached match
    else Cache miss
        App->>Server: POST /encourage (headline text)
        Server->>Claude: Messages API<br/>(theological matching prompt<br/>+ candidate quotes from DB)
        Claude-->>Server: Matched quote + figure name
        Server-->>App: EncouragementResponse DTO
        App->>Room: Cache by articleUrl
        Room-->>App: Emit match
    end
    App->>User: Display quote + figure portrait

    Note over Server,Scripture: Quote seeding — offline / batch

    Server->>Scripture: Fetch verse text<br/>(seed time, not on-demand)
    Scripture-->>Server: Verse content
```

## API roles

| API | When called | Purpose |
|-----|-------------|---------|
| The News API | On app launch + periodic refresh | Source live headlines |
| Anthropic Claude API | On headline tap (cache miss) | Match headline to theological quote |
| Scripture API | Batch seed time | Populate authoritative verse text for figures |

## Caching strategy

Matches are cached in Room by `articleUrl` (not headline ID — auto-increment IDs are unstable
across refreshes). A cache hit skips the Claude API call entirely, keeping cost and latency low
for repeated taps on the same headline.

## Claude prompt shape

The server sends Claude a theological matching prompt with:
- The headline text
- A set of candidate quotes from the DB (filtered by theme relevance)

Claude returns a single quote + figure name. The server wraps it into an `EncouragementResponse`
and the client caches it by `articleUrl`.
