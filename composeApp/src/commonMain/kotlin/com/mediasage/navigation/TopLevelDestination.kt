package com.mediasage.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Groups
import androidx.compose.ui.graphics.vector.ImageVector
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.nav_headlines
import mediasage.composeapp.generated.resources.nav_voices
import org.jetbrains.compose.resources.StringResource

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Route,
    val labelRes: StringResource,
    val icon: ImageVector
) {
    HEADLINES(
        route = Route.Home,
        labelRes = Res.string.nav_headlines,
        icon = Icons.Default.Home
    ),
    VOICES(
        route = Route.Figures,
        labelRes = Res.string.nav_voices,
        icon = Icons.Default.Groups
    )
}
