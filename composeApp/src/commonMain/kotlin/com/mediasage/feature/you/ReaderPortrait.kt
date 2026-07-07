package com.mediasage.feature.you

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.theme.BrandAmber

@Composable
internal fun FigurePortraitImage(
    imageUrl: String,
    name: String?,
    size: Dp,
    isToday: Boolean,
    isPast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = name,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (isToday) Modifier.solidCircleBorder(BrandAmber, 2.dp) else Modifier)
            .then(if (isPast) Modifier.alpha(0.6f) else Modifier),
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
    )
}

internal fun Modifier.solidCircleBorder(color: Color, strokeWidth: Dp): Modifier = drawBehind {
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - strokeWidth.toPx() / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = strokeWidth.toPx()),
    )
}
