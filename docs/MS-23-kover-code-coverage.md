# MS-23: Set up Test Code Coverage with Kover

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-19

## What was built

Kover 0.9.8 integrated across all modules for Kotlin code coverage tracking.

### Changes
- **`build.gradle.kts`** — Kover plugin applied to all subprojects alongside Detekt
- **`ci.yml`** — Tests now generate coverage reports (HTML + XML), uploaded as CI artifacts with 14-day retention
- **`libs.versions.toml`** — Added Kover 0.9.8 version and plugin entry

### Baseline coverage (server module)
- Line: 65.0%
- Method: 69.9%
- Class: 66.7%

## Key decisions & why

- **No threshold enforcement yet**: Current coverage is 65% line, below the Phase 2 target of 70%. Enforcing now would block PRs. Will add thresholds once more tests bring coverage above target.
- **HTML + XML reports**: HTML for human browsing, XML for potential CI integrations (PR comments, badges) later.
- **14-day artifact retention**: Long enough to review coverage trends, short enough to not bloat storage.
- **Coverage badge deferred**: Would require a third-party action or service. Low value until we have a threshold to display.

## Concepts learned

- **Kover vs JaCoCo**: Kover understands Kotlin constructs (inline functions, coroutines, data classes) that JaCoCo misreports. It's JetBrains-maintained and KMP-compatible.
- **`koverHtmlReport`**: Generates a browsable HTML report at `build/reports/kover/html/index.html`. Shows line-by-line coverage highlighting.
- **`koverXmlReport`**: Generates JaCoCo-compatible XML that tools can parse for metrics.
- **`upload-artifact@v4`**: GitHub Actions action that saves files from the build for download later. The `if: always()` ensures reports are uploaded even if tests fail.

## Gotchas

- Kover in KMP projects primarily covers JVM/Android targets. iOS (Kotlin/Native) coverage requires additional setup.
- Run `./gradlew koverHtmlReport` locally to browse coverage at `server/build/reports/kover/html/index.html`.
