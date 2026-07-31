package com.mediasage.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * A 60% sepia / 40% original-color blend (rather than the full-strength sepia matrix, which maps
 * every pixel fully into brown-monochrome and washes out a portrait's actual coloring) — each row
 * is `identity * 0.4 + fullSepia * 0.6`, so warm tone still comes through without flattening color.
 */
val SepiaColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        set(0, 0, 0.636f); set(0, 1, 0.461f); set(0, 2, 0.113f)
        set(1, 0, 0.209f); set(1, 1, 0.812f); set(1, 2, 0.101f)
        set(2, 0, 0.163f); set(2, 1, 0.320f); set(2, 2, 0.479f)
    }
)

val GrayscaleColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) }
)
