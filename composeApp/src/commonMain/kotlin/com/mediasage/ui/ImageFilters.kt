package com.mediasage.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val SepiaColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        set(0, 0, 0.393f); set(0, 1, 0.769f); set(0, 2, 0.189f)
        set(1, 0, 0.349f); set(1, 1, 0.686f); set(1, 2, 0.168f)
        set(2, 0, 0.272f); set(2, 1, 0.534f); set(2, 2, 0.131f)
    }
)
