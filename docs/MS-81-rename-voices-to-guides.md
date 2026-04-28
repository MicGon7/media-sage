# MS-81: Rename Voices to Guides

## What changed

Updated all user-visible strings in `strings.xml` that referred to the "Voices" tab/feature to say "Guides" instead. No Kotlin class, file, or resource key names were changed.

## String values updated

| Resource key | Before | After |
|---|---|---|
| `nav_voices` | Voices | Guides |
| `title_voices` | Gathered Voices | Gathered Guides |
| `voices_coming_soon` | Voice selection coming soon | Guide selection coming soon |
| `voices_empty_state` | Voices will appear as you read | Guides will appear as you read |

## What was intentionally left unchanged

- Kotlin file names (`FiguresScreen.kt`, `FiguresViewModel.kt`, `FiguresContract.kt`)
- Kotlin class names (`FiguresScreen`, `FiguresViewModel`, etc.)
- Private composable function names (`VoicesHeader`, `VoicesList`, `VoiceCard`)
- String resource keys (`nav_voices`, `title_voices`, `voices_*`) — these are code identifiers, not user-visible
- Import statements in `FiguresScreen.kt` referencing the resource keys
- Code comments (non-user-visible)

## Pattern note

When renaming a UI concept, update only the string **values** in `strings.xml`. Resource **keys** are code identifiers and renaming them would require updating every Kotlin import site — equivalent to renaming Kotlin identifiers, which is out of scope for a copy/label change.
