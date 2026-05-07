# MS-144: Move FigureDetail pin action from top bar into the name/role row

## What changed

The pin `IconButton` was removed from the `MediaSageBackRow` content lambda in `FigureDetailScreen` and repositioned into a `Row` alongside the figure name and role text in `FigureDetailContent`.

## Layout approach

Used `Modifier.weight(1f)` on the inner `Column` (name + role) inside a full-width `Row` to anchor the text at the leading edge and push the `IconButton` to the trailing edge. `verticalAlignment = Alignment.CenterVertically` on the `Row` ensures the pin icon is vertically centered relative to the stacked text.

```
Row(fillMaxWidth, verticalAlignment = CenterVertically) {
    Column(weight(1f)) { name text; role text }
    IconButton { pin icon }
}
```

## Data flow

`FigureDetailContent` previously had no knowledge of pin state. It now receives `onPinToggle: () -> Unit` from its parent and reads `state.isPinned` directly from the `Success` state it already holds — no new state hoisting needed.

## Top bar impact

The `MediaSageBackRow` content lambda now contains only the animated figure name. No other top bar elements were changed.
