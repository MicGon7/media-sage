# MS-24: Set up Detekt for Kotlin Static Analysis

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-19

## What was built

Detekt static analysis integrated across all modules with custom configuration and CI enforcement.

### Files
- **`detekt.yml`** — Custom config at project root with relaxed thresholds for complexity, disabled MagicNumber/WildcardImport rules, 140-char line limit
- **`build.gradle.kts`** — Detekt plugin applied to all subprojects via `subprojects {}` block
- **`ci.yml`** — Detekt step added before build steps (fails CI on violations)
- **`ErrorResponse.kt`** — Extracted from StatusPages.kt to fix MatchingDeclarationName violation

### Key config decisions
- `MagicNumber` disabled — too noisy for data classes with default values
- `WildcardImport` disabled — Ktor and Compose use wildcard imports extensively
- `MaxLineLength: 140` — wider than default 120, matches IDE settings
- `TooGenericExceptionCaught` disabled — StatusPages legitimately catches `Throwable`
- `LongParameterList` relaxed to 8/10 — data classes and DTOs often have many fields

## Concepts learned

- **Detekt in KMP**: Detekt 1.23.x only analyzes JVM/Android source sets, not `commonMain`. KMP `commonMain` shows as NO-SOURCE. Detekt 2.0 (alpha) will add KMP support but requires Gradle 9+.
- **`buildUponDefaultConfig`**: Means our `detekt.yml` only overrides specific rules — everything else uses Detekt's defaults. Less config to maintain.
- **MatchingDeclarationName rule**: Files with a single top-level declaration must have a matching filename. `ErrorResponse` in `StatusPages.kt` triggered this — moved to its own file.

## Gotchas

- Detekt 1.23.8 doesn't support Kotlin 2.3.x type resolution features, but basic linting works fine.
- `commonMain` source sets not analyzed — a known limitation until Detekt 2.0 stable.
