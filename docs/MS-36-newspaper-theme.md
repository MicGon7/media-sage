# MS-36: Newspaper-Style Material3 Theme

## What Changed
Added a custom Material3 theme with editorial newspaper styling and rebranded the app from "Media Sage" to "The New Life Times."

## Theme Architecture

Three files under `composeApp/src/commonMain/kotlin/com/mediasage/theme/`:

| File | Purpose |
|------|---------|
| `Color.kt` | Palette: white background, navy accents, ink text, card borders |
| `Type.kt` | Playfair Display (headlines), Lora (body). `mediaSageTypography()` accepts a `headlineFont` param for swapping |
| `Theme.kt` | `MediaSageTheme` composable wrapping Material3. Light + dark schemes defined, dark deferred to MS-30 |

## Fonts
- **Playfair Display** (variable TTF) — serif, editorial headlines
- **Lora** (variable TTF) — serif, readable body text
- Bundled in `composeResources/font/` (4 files: regular + italic for each)
- Loaded via Compose Multiplatform `Font()` resource API

## Color Palette (Light)
- **Background/Surface**: White (`#FFFFFF`)
- **Text**: Ink (`#1A1A1A`)
- **Primary/Accents**: Navy (`#1B2A4A`)
- **Cards**: White with border (`#E0E0E0`)
- **Dividers**: Rule line (`#D0D0D0`)

## Other Changes
- App name: "Media Sage" -> "The New Life Times"
- Figures tab renamed to "Voices" with Groups icon
- Scaffold, TopAppBar, NavigationBar explicitly set to theme surface color
- Dark theme forced off (MS-30)

## Design Decisions
- Chose white + navy over papyrus/cream to match Figma designs
- Serif fonts for both headlines and body to maintain newspaper feel
- `mediaSageTypography()` accepts a `headlineFont` parameter so Playfair Display and Lora can be swapped easily
- Explicit container colors on bars to prevent Material3 default tonal surfaces from leaking through

## Related Tickets
- MS-30: Dark theme support (deferred)
- MS-31: Image support for headlines and figures
- MS-32: Data model updates (role, lifespan, Voices rename)
- MS-33: Home screen layout
- MS-34: Match screen layout
- MS-35: Voices screen layout
