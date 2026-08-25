package com.mediasage.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.login_paper_white
import org.jetbrains.compose.resources.painterResource

/**
 * The white newspaper-page look shared by the login form panel and the reflection bottom sheet —
 * a shadowed white paper texture painted with [ContentScale.FillBounds]. No clip shape here: the
 * paper image's own alpha channel defines its deckled silhouette, so a rounded-rect clip would
 * slice its irregular edges into a hard corner.
 */
@Composable
fun Modifier.paperSurface(elevation: Dp = 8.dp, alpha: Float = 0.95f): Modifier = this
    .shadow(elevation = elevation, shape = MaterialTheme.shapes.medium, clip = false)
    .paint(
        painter = painterResource(Res.drawable.login_paper_white),
        contentScale = ContentScale.FillBounds,
        alpha = alpha,
    )
