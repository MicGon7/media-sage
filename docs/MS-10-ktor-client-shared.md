# MS-10: Ktor Client API Services in Shared Module

**Epic:** MS-2 (Shared Data Layer)
**Date completed:** 2026-04-19

## What was built

Client-side HTTP layer in the shared KMP module for the mobile app to communicate with the Media Sage server.

### Files
- **`data/remote/ApiDtos.kt`** — Client-side DTOs for news articles, match requests/results, and scripture verses/passages
- **`data/remote/MediaSageApi.kt`** — Interface defining all server endpoints
- **`data/remote/MediaSageApiImpl.kt`** — Implementation using Ktor HttpClient with configurable base URL
- **`data/remote/HttpClientFactory.kt`** — Common factory with JSON + timeout config, `expect/actual` for platform engines
- **Platform engines** — OkHttp (Android), Darwin (iOS)
- **`di/SharedModule.kt`** — Updated to provide HttpClient and MediaSageApi via Koin

### Data transformation chain
```
Server JSON → Client DTO → Room Entity → Domain Model → UI
```
- **DTOs** = server response shape (serialization only, no logic)
- **Entities** = database schema (Room annotations, stored types)
- **Domain Models** = clean types for UI (enums, lists, no annotations)
- **Repositories** bridge all three layers — Room is always the single source of truth

## Key decisions & why

- **Single `MediaSageApi` interface**: The mobile app talks to our server, not external APIs directly. One interface covers all endpoints.
- **`expect/actual` for HTTP engine**: Platform-specific engines (OkHttp/Darwin) with shared JSON and timeout config on top.
- **Base URL as parameter**: `sharedModule(serverBaseUrl)` — default `10.0.2.2:8080` for Android emulator. Configurable for real devices or iOS.
- **DTOs separate from server DTOs**: The shared module has its own DTOs matching the server's response format. Different modules, different concerns — if the server adds fields, only the client DTO needs updating.

## Concepts learned

- **`expect/actual` in KMP**: Declare `expect` function in commonMain, provide `actual` per platform. Compiler enforces all platforms have implementations.
- **Android emulator localhost**: Can't use `localhost` — use `10.0.2.2` to reach host machine.
- **DTO → Entity → Domain pipeline**: DTOs exist solely to serialize/deserialize JSON. They map to Entities for persistence, then Entities map to Domain Models for UI. The UI never sees DTOs or Entities.
- **`HttpClient.config { }`**: Adds plugins to an existing client — layers common config on top of platform-specific engine.

## Gotchas

- `HttpClientEngineFactory` not available in commonMain — return `HttpClient` directly from `expect/actual`.
- `10.0.2.2` only works for Android emulator. Real devices need the machine's actual IP.
- `sharedModule` changed from `val` to `fun` to accept `serverBaseUrl` parameter.
