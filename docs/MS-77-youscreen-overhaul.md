# MS-77: YouScreen Overhaul with Theme Picker

## "Reader" Tab and Screen Title

The bottom nav tab and screen title were renamed from "You" to **"Reader"** — thematic to the newspaper identity ("The Courage Post") and avoids the social-media connotation of "Profile".

## What Changed

Overhauled the YouScreen and SettingsScreen to match a structured profile + settings design. Moved theme controls out of YouScreen into SettingsScreen and connected Supabase `full_name` metadata as the display name.

## YouScreen

Replaced a placeholder greeting with a proper profile header:
- Circular 72dp avatar placeholder (`Box` + `CircleShape` clip + `Person` icon)
- Display name text (first name from Supabase, email fallback)
- Themed `HorizontalDivider` separator (primary color)
- Saved and History `MediaSageButton` row

A time-based greeting is shown above (or in place of) the display name: "Good morning/afternoon/evening, Michael". The greeting is computed at ViewModel init using `Instant.fromEpochMilliseconds(epochMillis()).toLocalDateTime()` — the same pattern used in `HomeViewModel.currentTone()`. Boundaries: `hour < 12` → morning, `hour < 17` → afternoon, else evening.

Display name sources from `AuthRepository.currentSession()`, which reads `userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull` from the Supabase session. Only the first name is shown (`substringBefore(" ")`).

## SettingsScreen

Full rewrite with scrollable sections:
- **Appearance**: `SingleChoiceSegmentedButtonRow` for Classic/Modern/Future theme; `Switch` for dark mode
- **Account**: Edit Profile (chevron placeholder), app version
- **Support**: Privacy Policy, Terms of Service, Send Feedback (all chevron placeholders)
- **Sign Out**: `Button` with `errorContainer` color anchored below the scroll area

Theme and dark mode state flow through `SettingsViewModel`, which combines `ThemePreferencesRepository.appTheme` and `ThemePreferencesRepository.darkMode`. `AppViewModel` exposes these same flows so `App.kt` can pass `theme` and `darkTheme` to `MediaSageTheme`.

## MVI Contract Changes

`YouContract` simplified — removed `darkMode`/`ToggleDarkMode` (moved to SettingsScreen), added `displayName: String`.

`SettingsContract` expanded — `UiState.Ready` now holds `appTheme`, `darkMode`, and `appVersion`. New intents: `SetAppTheme`, `ToggleDarkMode`, `SignOut`. New side effect: `SignedOut`.

## Supabase Display Name

`UserSession` model gained `displayName: String? = null`. Both `observeAuthState()` and `currentSession()` in `AuthRepositoryImpl` now read it from `user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull`.

`contentOrNull` requires an explicit import (`kotlinx.serialization.json.contentOrNull`) — it is not pulled in by `jsonPrimitive` alone.

To set a user's display name in Supabase directly via SQL:
```sql
UPDATE auth.users
SET raw_user_meta_data = raw_user_meta_data || '{"full_name": "Michael Gonzalez"}'::jsonb
WHERE email = 'test1@mediasage.dev';
```

## AppTheme Enum

Added a `label: String` property (`"Classic"`, `"Modern"`, `"Future"`) used as the `SegmentedButton` display text. `ThemePreferencesRepository` persists the selected theme by enum name via `stringPreferencesKey("app_theme")`.
