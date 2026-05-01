# MS-108: Client Figure Sync

## What was built

The client now syncs the server-owned figure roster on every app launch via `GET /api/figures`. Figures are cached in Room and drive the Guides tab directly — no longer derived from cached encouragements.

## Data flow

```
App launch → AppViewModel.init → FigureRepository.syncFigures()
    → DELETE all figures → INSERT 100 from server → Room figures table
    → FiguresViewModel observes getAllFigures() Flow → Guides tab renders
```

## AppViewModel

Added `AppViewModel` as the app-level bootstrapper, instantiated in `App.kt` via `koinViewModel<AppViewModel>()`. It survives config changes on Android and runs once per session — the right place for any app-level init (figure sync today, auth checks in the future).

`FiguresViewModel` is now purely reactive — it only observes `FigureRepository.getAllFigures()` and does not trigger a sync itself.

## Sync strategy

`syncFigures()` does `deleteAll()` then `insertAll()` on every launch. The server is the source of truth — whatever it returns replaces the local cache. This means:

- Server `isEnabled` toggle takes effect on next app launch
- No stale figures
- Simple and consistent

**Future consideration**: when MS-105 adds `isUnlocked`, the sync strategy must change to preserve unlock state. See MS-105 for full architecture notes.

## FigureDetailViewModel

Bio is now read from the stored `FigureEntity.bio` field (seeded from Wikipedia by the server at startup) rather than fetching from the Wikipedia API on every detail screen open. The Wikipedia API call on the client has been removed.

## FigureCategory enum

Updated from 4 values (THEOLOGIAN, MYSTIC, MODERN, BIBLICAL) to 6:

| Value | Display name |
|---|---|
| THEOLOGIAN | Theologians & Reformers |
| MYSTIC | Mystics & Contemplatives |
| CHURCH_FATHER | Church Fathers |
| SOCIAL_JUSTICE | Social Justice & Public Faith |
| INTELLECTUAL | Scientists & Intellectuals |
| MISSIONARY | Missionaries & Servants |

Categories are internal only — never surfaced as UI labels. The `role` field drives display.

## FigureEntity schema changes (DB version 10 → 11)

| Field | Change |
|---|---|
| `description` | Removed — replaced by `bio` |
| `bio` | Added — Wikipedia summary from server |
| `themes` | Added — comma-separated, populated in MS-102 |
| `portraitUrl` | Added — nullable, populated in MS-104 |

DB uses `fallbackToDestructiveMigration` — no manual migration file needed.

## OkHttp logging interceptor

Added `com.squareup.okhttp3:logging-interceptor` to the Android HTTP client at `BASIC` level. Filter Logcat by `okhttp.OkHttpClient` to see all network requests and response codes. Useful for catching early app-launch calls that the Android Studio Network Inspector misses.

## Dead code removed

`InitialFigures.kt` deleted — it was never wired up and predated the server-owned figure store.

## Smoke test

- `GET /api/figures` called on launch (verified via OkHttp Logcat + server logs)
- 100 figures appear in Guides tab on first launch
- Subsequent launches sync silently; figures persist in Room between launches
- Sync failure is caught and non-fatal — app works offline with cached figures
