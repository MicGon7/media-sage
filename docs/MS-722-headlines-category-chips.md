# MS-722: Headlines category filter — tabs replaced with comic filter chips

## What changed

The Headlines category row (`CategoryTabRow`) was a `MediaSageTabRow` in scrollable mode. It's now
`CategoryChipRow` — a `LazyRow` of single-select Material3 `FilterChip`s, one per
`HeadlineCategoryFilter`, with the selected chip painted in the shared comic gradient. Chips size to
their labels, so longer categories ("Business", "Science") render fully instead of ellipsizing —
the actual bug in the ticket.

`MediaSageTabRow` shrank back to its original MS-679 four-param signature: the six knobs MS-719 added
for this one call site (`scrollable`, `showBackground`, `edgePadding`, `singleBottomIndicator`,
`tabHeight`, `labelStyle`) are gone along with the `PrimaryScrollableTabRow` branch, since the two
remaining callers (`FigureDetailScreen`, `DayDetailScreen`) only ever used the defaults.

## Why tabs couldn't fit the labels

Scrollable mode wasn't a fix either: `PrimaryScrollableTabRow` floors every tab at Material's private
`ScrollableTabRowMinimumTabWidth = 90.dp`, and `Tab`'s text slot adds 16dp horizontal padding per
side. Short labels ("World") pad out to 90dp, reading as huge gaps between tabs. There's no public
API to lower the floor — for a compact, content-sized filter row, tabs are the wrong component.
M3 guidance agrees: tabs switch peer views; **filter chips** filter content within one view, and M3
merged M2's single-select ChoiceChip into `FilterChip`, so a single-select chip row is idiomatic.

## Painting a gradient on a FilterChip

`SelectableChipColors` only accepts flat `Color`s — no `Brush` slot. Keeping `FilterChip` (for its
selected-state semantics and checkmark animation) with the comic look required:

- `selectedContainerColor = Color.Transparent`
- the gradient painted by a sibling `Box` **behind** the chip, sourced from
  `rememberComicSurfaceColors(Horizontal)` — the same single source of styling `MediaSageSurface`
  renders, so the chips are visual siblings of the Reflect/Study/Share pills and inherit its
  dark-mode rule (neutral elevated surface instead of warm tones)

The trap: the chip's visible 32dp pill (`FilterChipDefaults.Height`) sits centered inside an
invisible 48dp minimum-touch-target layout. Any background/clip modifier on the chip itself paints
the full 48dp stadium and halos outside the border. Pinning `Modifier.height(32.dp)` fixes the halo
but shrinks the tap target below the accessibility minimum. The correct geometry: the sibling `Box`
uses `matchParentSize()` inset vertically by
`(LocalMinimumInteractiveComponentSize.current - FilterChipDefaults.Height) / 2`, landing exactly on
the pill's real bounds while the 48dp target stays intact.

## Edge-to-edge without the layout hack

The tab version escaped `ScreenHeader`'s 16dp inset with a custom `horizontalBleed` `layout` modifier
(measure wider than constraints, report the original width). Deleted. The sticky header is now a
`Column`: `ScreenHeader` keeps its own 16dp padding, and the chip row sits below it full-width with
`contentPadding = 16.dp` on the `LazyRow` — chips align with the title at rest but scroll under the
screen edge, which is the idiomatic scroll-under-padding pattern rather than lying to the parent
about measured width.
