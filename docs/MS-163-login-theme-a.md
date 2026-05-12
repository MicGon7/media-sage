# MS-163: Change LoginScreen Default Theme to Theme A (Navy → DarkBackground)

## What Changed

The default background gradient on `LoginScreenContent` was updated from Theme C (`NavyLight → Navy`) to Theme A (`Navy → DarkBackground`). Preview annotations were updated to reflect the new active theme.

## File Changed

`composeApp/src/commonMain/kotlin/com/mediasage/feature/login/LoginScreen.kt`

### `backgroundColors` default

```kotlin
// Before
backgroundColors: List<Color> = listOf(NavyLight, Navy)

// After
backgroundColors: List<Color> = listOf(Navy, DarkBackground)
```

### Preview comment and annotations

```kotlin
// Before
// Theme comparisons - reference only, Theme C is the active default
@Preview(showBackground = true, name = "Theme A - Navy → Dark")
...
@Preview(showBackground = true, name = "Theme C - NavyLight → Navy (active)")

// After
// Theme comparisons - reference only, Theme A is the active default
@Preview(showBackground = true, name = "Theme A - Navy → Dark (active)")
...
@Preview(showBackground = true, name = "Theme C - NavyLight → Navy (reversed)")
```

## Why

Theme A (Navy → DarkBackground) creates a deeper, richer dark gradient suited to the brand's visual direction. Theme C (NavyLight → Navy) is lighter at the top and was the interim default during initial design exploration.

## Pattern

Login screen themes are held in named `@Preview` functions with explicit `backgroundColors` arguments, making it easy to compare themes visually in Android Studio without changing the default. The active theme is indicated by the `(active)` suffix in the preview name and by the default parameter value on `LoginScreenContent`.
