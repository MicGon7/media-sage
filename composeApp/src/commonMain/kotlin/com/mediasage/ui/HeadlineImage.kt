package com.mediasage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun HeadlineImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
    height: Dp = 64.dp,
    colorFilter: ColorFilter? = null
) {
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier.size(width = width, height = height),
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
        )
    } else {
        HeadlinePlaceholder(modifier = modifier, width = width, height = height)
    }
}

@Composable
private fun HeadlinePlaceholder(
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
    height: Dp = 64.dp
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(height / 2)
        )
    }
}
