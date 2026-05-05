# MS-138: Fix delta sync cursor — use server timestamp instead of client clock

## Problem

The figures delta sync stored `currentTimeMillis()` (device clock) as the cursor in `sync_meta.last_figure_sync_at`. The server's `updated_at` trigger uses `EXTRACT(EPOCH FROM NOW()) * 1000` (Supabase/Postgres server clock). When the device clock is even slightly ahead of the server clock, a figure updated after a sync gets an `updated_at` that is *less than* the stored cursor — and the delta query `WHERE updated_at > since` misses it.

Confirmed via testing: fresh install (full sync, `since=null`) picked up the updated portrait URL correctly; a delta sync on restart did not.

## Fix

The server now wraps the `/api/figures` response in an envelope:

```json
{
  "syncedAt": 1746452000000,
  "figures": [ ... ]
}
```

`syncedAt` is `System.currentTimeMillis()` read on the server **before** querying the DB, so it is always on the same clock as the `updated_at` trigger. The client stores `response.syncedAt` as the next sync cursor instead of its local `currentTimeMillis()`.

## Changes

| File | Change |
|---|---|
| `server/routes/FigureRoutes.kt` | Reads `syncedAt = System.currentTimeMillis()` before query, responds with `FiguresResponse` envelope |
| `server/repository/FigureRepository.kt` | Added `FiguresResponse(syncedAt, figures)` data class |
| `shared/data/remote/ApiDtos.kt` | Added `FiguresResponse(syncedAt, figures)` client DTO |
| `shared/data/remote/MediaSageApi.kt` | `getFigures()` return type changed from `List<FigureDto>` to `FiguresResponse` |
| `shared/data/remote/MediaSageApiImpl.kt` | Deserializes to `FiguresResponse` |
| `shared/data/repository/FigureRepositoryImpl.kt` | Stores `response.syncedAt` in `sync_meta` instead of `currentTimeMillis()` |
| `server/test/FigureSinceRouteTest.kt` | Updated to deserialize `FiguresResponse` envelope |
| `shared/test/EncouragementRepositoryTest.kt` | Fake `getFigures()` returns `FiguresResponse` stub |

## Why capture syncedAt before the query?

Reading the timestamp before the DB query means any figure updated during the query window will have `updated_at >= syncedAt`. On the next delta sync, those figures will be included. If we read the timestamp after the query, a figure updated between query-start and query-end could be missed.
