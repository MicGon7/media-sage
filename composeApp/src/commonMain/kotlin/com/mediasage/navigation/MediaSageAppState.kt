package com.mediasage.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
@Stable
class MediaSageAppState(
    val backStack: NavBackStack<NavKey>
) {
    val currentDestination: NavKey?
        get() = backStack.lastOrNull()

    val isTopLevel: Boolean
        get() = currentDestination in TopLevelDestination.entries.map { it.route }

    val showBottomBar: Boolean
        get() = isTopLevel

    fun navigateToTopLevel(destination: TopLevelDestination) {
        if (currentDestination != destination.route) {
            backStack.clear()
            backStack.add(destination.route)
        }
    }

    fun navigateToHeadlineDetail(articleUrl: String) {
        backStack.add(Route.HeadlineDetail(articleUrl))
    }

    fun navigateToFigureDetail(figureId: Long) {
        backStack.add(Route.FigureDetail(figureId))
    }

    fun navigateToBookmarks() {
        backStack.add(Route.Bookmarks)
    }

    fun navigateToHistory() {
        backStack.add(Route.History)
    }

    fun navigateToSettings() {
        backStack.add(Route.Settings)
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
        Route.Briefing
    )
    return remember(backStack) { MediaSageAppState(backStack) }
}
