package com.mediasage.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.ui.graphics.vector.ImageVector
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.nav_briefing
import mediasage.composeapp.generated.resources.nav_headlines
import mediasage.composeapp.generated.resources.nav_voices
import mediasage.composeapp.generated.resources.nav_you
import org.jetbrains.compose.resources.StringResource

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Route,
    val labelRes: StringResource,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BRIEFING(
        route = Route.Briefing,
        labelRes = Res.string.nav_briefing,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    HEADLINES(
        route = Route.Home,
        labelRes = Res.string.nav_headlines,
        selectedIcon = Icons.Filled.ViewHeadline,
        unselectedIcon = Icons.Outlined.ViewHeadline
    ),
    SAGES(
        route = Route.Figures,
        labelRes = Res.string.nav_voices,
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups
    ),
    YOU(
        route = Route.You,
        labelRes = Res.string.nav_you,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}
