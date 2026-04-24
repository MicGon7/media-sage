# MS-58: Themed Loading Animation for Encouragement Screen

## What Was Done

Replaced the generic `CircularProgressIndicator` in `EncouragementLoading()` with a Lottie book animation, tinted to the app's primary theme color.

## Implementation

- Added `compottie` and `compottie-dot` dependencies for Compose Multiplatform Lottie support
- Bundled `book_loader.lottie` in `composeResources/files/`
- Loaded via `Res.readBytes("files/book_loader.lottie")` inside `rememberLottieComposition`
- Applied `ColorFilter.tint(MaterialTheme.colorScheme.primary)` to match the app theme
- Falls back to `CircularProgressIndicator` while the composition loads

## Key Learnings

### compottie Setup

Use `compottie` + `compottie-dot` for `.lottie` (dotLottie) file support. Do NOT add `compottie-resources` alongside these — it re-exports the same classes causing ambiguous import errors.

### DotLottie API

```kotlin
val composition by rememberLottieComposition {
    LottieCompositionSpec.DotLottie(
        Res.readBytes("files/book_loader.lottie")
    )
}
val progress by animateLottieCompositionAsState(
    composition = composition,
    iterations = Int.MAX_VALUE
)
Image(
    painter = rememberLottiePainter(composition = composition, progress = { progress }),
    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
)
```

### Animation Files

Store `.lottie` files in `composeResources/files/`. Source from [LottieFiles.com](https://lottiefiles.com) free tier.
