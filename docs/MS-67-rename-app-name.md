# MS-67: Rename Public-Facing App Name to "The Media Sage"

## What Changed

Replaced every user-visible occurrence of "The New Life Times" with "The Media Sage" across strings, prompts, and docs.

## Files Updated

| File | Change |
|------|--------|
| `composeApp/src/commonMain/composeResources/values/strings.xml` | `app_name` and `title_home` strings |
| `composeApp/src/androidMain/res/values/strings.xml` | Android `app_name` string |
| `server/.../ClaudeApiService.kt` | System prompt reference in `ENCOURAGE_SYSTEM_PROMPT` |
| `docs/MS-33-home-screen-layout.md` | Masthead description |
| `docs/MS-36-newspaper-theme.md` | Historical rebrand notes |

## Scope Notes

- No package names, class names, or code identifiers changed — purely user-visible strings.
- The iOS `Info.plist` at `iosApp/iosApp/Info.plist` had no display name entry, so no change was needed there.
- CLAUDE.md was already updated before this ticket was created.

## Verification

Confirmed zero occurrences of "The New Life Times" remain in the repo via grep. All tests and detekt passed with no changes to logic.
