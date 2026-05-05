# MS-141: Fix Delta Sync — Use Server ID as Room Primary Key

## Problem

Portrait URL changes in Supabase only appeared after a fresh install. Delta sync (the weekly incremental path) never updated existing Room rows.

## Root Cause

`FigureDto.toEntity()` mapped the server `id` to `serverId` only, leaving `id = 0` (the `FigureEntity` default). With `@PrimaryKey(autoGenerate = true)`, Room treats `id = 0` as "generate a new ID." So every delta sync inserted a fresh duplicate row instead of replacing the existing one — even though the DAO uses `OnConflictStrategy.REPLACE`. `INSERT OR REPLACE` resolves conflicts by primary key, and with `id = 0` it never found a match.

```kotlin
// Before — id defaults to 0, auto-generated → duplicate rows on delta sync
fun FigureDto.toEntity() = FigureEntity(
    name = name, ..., serverId = id   // id = 0 → new row
)

// After — server ID used as PK → INSERT OR REPLACE updates the existing row
fun FigureDto.toEntity() = FigureEntity(
    id = id, name = name, ..., serverId = id
)
```

## Why No Migration Was Needed

`@PrimaryKey(autoGenerate = true)` only auto-generates when `id == 0`. Passing a non-zero value always uses that value directly. The column definition (`INTEGER PRIMARY KEY AUTOINCREMENT`) is unchanged — only the data inserted changes. No Room migration required.

## Key Insight

Full sync (`deleteAll` + `insertAll`) masked the bug: clearing the table first meant duplicates never accumulated. Delta sync exposed it because the old row persisted alongside the new one. Always test upsert paths with existing data, not just on empty tables.

## Files Changed

- `shared/.../data/mapper/EntityMappers.kt` — `FigureDto.toEntity()` adds `id = id`
- `shared/.../data/mapper/EntityMappersTest.kt` — new `figureDtoToEntityUsesServerIdAsPrimaryKey` test
