# MS-205: Rename Future Theme to Warm

## Summary

Renamed the `AppTheme.FUTURE` enum entry to `AppTheme.WARM` across the theme layer to better reflect the palette's character — a warm sepia light / dark night mode inspired by Kindle e-reader aesthetics. "Warm" is more descriptive than the forward-looking connotation of "Future".

## Files Changed

| File | Change |
|------|--------|
| `AppTheme.kt` | `FUTURE("Future")` → `WARM("Warm")` |
| `Color.kt` | Comment `// Future palette` → `// Warm palette` |
| `Theme.kt` | Comment, internal function names (`futureLightColors` → `warmLightColors`, etc.), and `AppTheme.FUTURE` when-branch references updated to `WARM` |
| `ThemePreview.kt` | Preview names (`"Future Light"` → `"Warm Light"`) and function names updated |
| `SettingsScreen.kt` | Preview function `SettingsScreenFuturePreview` → `SettingsScreenWarmPreview`; `AppTheme.FUTURE` → `AppTheme.WARM` |
| `YouScreen.kt` | Preview function `YouScreenFuturePreview` → `YouScreenWarmPreview`; `AppTheme.FUTURE` → `AppTheme.WARM` |

## Pattern

For enum renames in Kotlin, prefer a global search for both the enum constant (`FUTURE`) and its string label (`"Future"`) plus any related symbol names (function prefixes like `future*`). A ripgrep pass after edits confirms no residual references.

Internal helper functions in `Theme.kt` that prefix with the theme name (e.g. `futureLightColors`) should also be renamed for consistency — leaving them named after the old enum would create a confusing mismatch between the public API (`AppTheme.WARM`) and the private implementation (`futureLightColors`).

## Quality Gates

- `./scripts/run-affected-tests.sh` — skipped (composeApp changes require Android SDK; CI is the authoritative gate).
- `./gradlew detekt` — blocked by a filesystem `Operation not permitted` error when creating `gradle-api-8.14.3.jar` in the Gradle cache. This is an environment constraint in the container; CI runs detekt with full filesystem access.
