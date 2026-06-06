UI mockup skill — preview-driven UI exploration in assisted mode. This skill never produces a PR or commit. It ends when the developer is satisfied with the previews.

## 1. Reference Image

Ask the developer upfront: "Do you have a reference image — Figma export, screenshot, sketch, or photo?"

- **Figma export or high-fidelity screenshot**: Use as the design source of truth. Match layout, spacing, typography hierarchy, and visual weight as closely as Compose allows. Call out anything that cannot be reproduced exactly and propose the closest idiomatic Compose alternative.
- **Sketch or low-fidelity reference**: Interpret intent, not pixel precision. State what was interpreted and why.
- **Mid-session image**: If the developer pastes a new image during iteration, explicitly acknowledge it and treat it as the new source of truth from that point forward.
- **No image**: Proceed from the developer's verbal description alone.

## 2. Determine Target

Check whether the target screen or component already exists in `composeApp/src/commonMain/kotlin/com/mediasage/feature/`.

**Existing screen or component:**
- Add `@Preview` functions to the existing file — do not modify any existing composable logic, only add previews at the bottom of the file.

**Component does not exist yet:**
- Create the file in the correct package: `composeApp/src/commonMain/kotlin/com/mediasage/feature/{name}/{Name}.kt`
- Write the composable and `@Preview` functions together in the same file.
- Track that this file was created by this skill — needed for cleanup.

## 3. Single Screen or Component

**Every `@Preview` function must be a single line that calls a named private composable.** Never put layout code directly inside a `@Preview` function — Android Studio renders named composables faster and the preview pane becomes responsive immediately.

```kotlin
// ✅ Correct — preview is one line, layout is in a named composable
@Preview(name = "HeadlineCard — Default")
@Composable
private fun HeadlineCardPreview() {
    HeadlineCardContent()
}

@Composable
private fun HeadlineCardContent() {
    MediaSageTheme {
        HeadlineCard(
            headline = "World leaders gather for climate summit",
            source = "Reuters",
            publishedAt = "2h ago"
        )
    }
}

// ❌ Wrong — layout blob inside the @Preview function, slow to render
@Preview(name = "HeadlineCard — Default")
@Composable
private fun HeadlineCardPreview() {
    MediaSageTheme {
        Column {
            Text("World leaders gather...")
            // ... dozens of lines ...
        }
    }
}
```

For exploration themes, define colors as private `val`s above the composable — not inside it:

```kotlin
private val ExplorationBackground = Color(0xFF12140A)
private val ExplorationPrimary = Color(0xFFCCFF33)

@Composable
private fun HeadlineCardExplorationContent() {
    // Exploration only — replace with MediaSageTheme before implementing
    MaterialTheme(colorScheme = darkColorScheme(primary = ExplorationPrimary, ...)) {
        HeadlineCard(...)
    }
}
```

## 4. Multi-Screen Flow

If the developer requests a flow (e.g. Headlines → Briefing → Figures), add all screens as separate `@Preview` functions in the same file named `Mockups.kt` in the first screen's package.

- Name previews clearly: `HeadlinesScreenPreview`, `BriefingScreenLoadingPreview`, `BriefingScreenSuccessPreview`
- Design decisions established on the first screen (spacing, typography scale, color) carry forward to all subsequent screens — do not treat each screen as fresh
- The developer reviews the full flow by scrolling the Android Studio preview pane

## 5. Exploration Themes

When exploring colors or layouts that differ from `MediaSageTheme`, use an inline theme override inside the `@Preview` function:

```kotlin
@Preview(name = "HeadlineCard — Exploration")
@Composable
fun HeadlineCardExplorationPreview() {
    // Exploration only — replace with MediaSageTheme before implementing
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A1A2E),
            surface = Color(0xFFF5F5F5)
        )
    ) {
        HeadlineCard(...)
    }
}
```

- Never create a separate theme file for exploration
- When a flow spans multiple screens, apply the same inline theme to all previews in the file

## 6. After Each Iteration

Tell the developer:
- The exact file path of the preview file
- The name of each `@Preview` function added
- Where to find them in Android Studio (Preview pane, right side of the editor)

Then ask: "What would you like to change, or are you done?"

Do NOT open a PR, commit, or transition any Jira ticket.

## 7. Cleanup

When the developer says they are done and do not want to keep the previews:

- **File was created by this skill**: Delete the entire file.
- **File already existed**: Remove only the `@Preview` functions that were added — leave all existing composable code untouched.

## 8. Component Extraction

Before handing off, scan what was built and identify any composables that are generic enough to be reusable across the app — custom text field styling, buttons, cards, dividers, or any styled wrapper used more than once in the mockup.

If any are found, ask the developer:

> "I notice [X] and [Y] look reusable across the app. Would you like me to extract them as named components under `composeApp/src/commonMain/kotlin/com/mediasage/ui/`? I'd name them following the project convention — e.g. `MediaSageOutlinedTextField`, `MediaSagePrimaryButton`."

- **Yes** → create the component file(s) in `composeApp/src/commonMain/kotlin/com/mediasage/ui/` using the `MediaSage` prefix, then update the mockup to use them
- **No** → leave inline, note it in the `/ticket-work` handoff
- Check `composeApp/src/commonMain/kotlin/com/mediasage/ui/` first — if a component already exists that covers the pattern (e.g. `MediaSageButton.kt`), use it instead of creating a duplicate

## 9. Wrapping Up

When the developer is satisfied and wants to keep the work, remind them:

- If any preview uses an exploration theme (`MaterialTheme(colorScheme = ...)`), it must be replaced with `MediaSageTheme` before implementing
- If a new component was created and is being kept, the next step is a `/ticket-work` ticket to wire it into the real screen
- Previews are not committed by this skill — commit them yourself if you want them in version control
