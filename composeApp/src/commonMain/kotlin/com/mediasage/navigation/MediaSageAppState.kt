package com.mediasage.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.title_voices
import mediasage.composeapp.generated.resources.title_home
import mediasage.composeapp.generated.resources.title_match
import org.jetbrains.compose.resources.StringResource

@Stable
class MediaSageAppState(
    val backStack: NavBackStack<NavKey>
) {
    val currentDestination: NavKey?
        get() = backStack.lastOrNull()

    val isTopLevel: Boolean
        get() = currentDestination in TopLevelDestination.entries.map { it.route }

    val titleRes: StringResource
        get() = when (currentDestination) {
            is Route.Home -> Res.string.title_home
            is Route.Match -> Res.string.title_match
            is Route.Figures -> Res.string.title_voices
            else -> Res.string.title_home
        }

    fun navigateToTopLevel(destination: TopLevelDestination) {
        if (currentDestination != destination.route) {
            backStack.clear()
            backStack.add(destination.route)
        }
    }

    fun navigateToMatch(headlineId: Long) {
        backStack.add(Route.Match(headlineId))
    }

    fun navigateBack() {
        backStack.removeLastOrNull()
    }
}

@Composable
fun rememberMediaSageAppState(): MediaSageAppState {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = navSerializersModule
        },
        Route.Home
    )
    return remember(backStack) { MediaSageAppState(backStack) }
}
