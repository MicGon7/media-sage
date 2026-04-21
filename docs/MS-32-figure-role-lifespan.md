# MS-32: Add Role and Lifespan Fields to Figure Data Model

## What Changed
Added `role` and `lifespan` fields to the Figure data model at all layers (entity, domain, mapper). Created seed data for 10 initial figures matching the Figma designs.

## Data Model Changes

### FigureEntity (Room)
Added two new columns with empty string defaults (backward compatible):
- `role: String` — Display title, e.g., "Theologian & Martyr", "Monk & Contemplative Writer"
- `lifespan: String` — Birth-death years, e.g., "1906-1945", "1936-present"

### Figure (Domain Model)
Same two fields added with empty string defaults.

### Mapper
Updated `toDomain()` and `toEntity()` to map the new fields.

## Database Migration
- Version bumped from 1 to 2
- Using `fallbackToDestructiveMigration(dropAllTables = true)` on both Android and iOS database builders
- Safe for pre-release — no production data to preserve
- Replace with proper migration before any public release

## Initial Figures
`InitialFigures.kt` provides 10 initial figures matching the Figma "Voices" screen designs:
1. Dietrich Bonhoeffer — Theologian & Martyr
2. Watchman Nee — Church Leader & Author
3. Julian of Norwich — Medieval Mystic
4. Dorothy Day — Social Activist & Servant
5. Pope Francis — Bishop of Rome
6. Augustine of Hippo — Bishop & Church Father
7. Corrie ten Boom — Holocaust Survivor & Evangelist
8. Francis of Assisi — Friar & Founder
9. C.S. Lewis — Author & Apologist
10. Mother Teresa — Nun & Missionary

Initial figures are not yet wired into DI — they will be integrated when the database is connected to the app module.

## Design Notes
- `role` is distinct from `category`: category is the enum (THEOLOGIAN, MYSTIC, etc.) used for filtering, role is the human-readable display string
- `lifespan` uses "present" for living figures
- Both fields default to empty string so existing code doesn't break
