# MS-98: Rename App Name to "Nick's Demo"

## What changed

Renamed the public-facing app name from "The Media Sage" to "Nick's Demo" in all user-visible string resources.

## Files updated

- `composeApp/src/commonMain/composeResources/values/strings.xml` — `app_name` and `title_home`
- `composeApp/src/androidMain/res/values/strings.xml` — `app_name`

## What was intentionally not changed

- `CLAUDE.md` — project documentation describing the app's canonical identity; not user-facing
- `server/src/main/kotlin/com/mediasage/server/service/ClaudeApiService.kt` — Claude system prompt; backend-only, not visible to end users
- Historical docs in `docs/` — preserve the record of prior rename decisions

## Pattern

App name changes are a string resource–only operation. The single source of truth for UI display names is `strings.xml`; the Android-specific `res/values/strings.xml` must be updated alongside the KMP common resource file to keep both launchers in sync.
