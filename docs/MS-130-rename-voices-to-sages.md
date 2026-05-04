# MS-130: Rename Voices Tab to Sages

## What Changed

The bottom navigation tab previously labeled "Guides" (originally "Voices") was renamed to "Sages" to align with the app's theme and branding — *The Media Sage*.

## Files Modified

- `composeApp/src/commonMain/composeResources/values/strings.xml`
  - `nav_voices` value: `"Guides"` → `"Sages"` (bottom nav tab label)
  - `title_voices` value: `"Gathered Guides"` → `"The Sages"` (screen header title)
- `composeApp/src/commonMain/kotlin/com/mediasage/navigation/TopLevelDestination.kt`
  - Enum entry `VOICES` → `SAGES` (kept `labelRes = Res.string.nav_voices` key unchanged to avoid unnecessary churn)

## Key Decision

String resource *keys* (e.g. `nav_voices`, `title_voices`) were left as-is. Renaming keys would require updating all import references in Kotlin files with no user-visible benefit. The resource *values* are what users see.

The `VOICES` enum entry was renamed to `SAGES` for internal consistency — a reader expecting "Sages" in the UI shouldn't encounter a `VOICES` constant. There were no direct references to `TopLevelDestination.VOICES`; the entry is consumed only via `entries` enumeration.
