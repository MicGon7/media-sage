package com.mediasage.rendertest

/**
 * Shared Robolectric `@Config(qualifiers = …)` values for UI render tests (see
 * docs/MS-581-headless-ui-render-loop.md). Robolectric's default screen (320x470dp) crops any
 * scrollable form taller than a small phone. Reuse [TALL_SCROLLABLE] instead of inventing a new
 * qualifier string per screen — a new qualifier pays a one-time Robolectric cold-start cost
 * (~15 min) until the worker image's next pre-bake warms it, so a second one-off qualifier is a
 * second cold start.
 */
object RenderTestDevices {
    const val TALL_SCROLLABLE = "w411dp-h891dp"
}
