# MS-31: Add Image Support for Headlines and Figures

## What Changed
Added Coil 3 for async image loading and created reusable image composables for headlines and figure placeholders.

## Dependencies Added
- `coil-compose` (3.4.0) — Compose Multiplatform image loading
- `coil-network-ktor3` (3.4.0) — Ktor network engine for Coil

## New Composables

### `HeadlineImage` (`ui/HeadlineImage.kt`)
Loads a headline thumbnail from a URL using Coil's `AsyncImage`. Falls back to a styled placeholder (Article icon on surfaceVariant background) when `imageUrl` is null.

Parameters:
- `imageUrl: String?` — URL from News API response
- `contentDescription: String?`
- `size: Dp` — defaults to 80.dp

### `FigurePlaceholder` (`ui/FigurePlaceholder.kt`)
Displays a figure's initials in a navy circle. Used until real portraits are available (MS-38).

Parameters:
- `name: String` — full name, initials are extracted automatically
- `size: Dp` — defaults to 64.dp

## Package Structure
New `ui/` package for shared composables:
```
composeApp/src/commonMain/kotlin/com/mediasage/
├── ui/
│   ├── HeadlineImage.kt
│   └── FigurePlaceholder.kt
```

## Notes
- Headline entity already has `imageUrl` field — no migration needed
- Figure portraits deferred to MS-38
- Coil handles caching, memory management, and placeholder states internally
