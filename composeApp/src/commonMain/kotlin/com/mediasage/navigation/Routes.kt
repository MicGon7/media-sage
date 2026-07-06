package com.mediasage.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Type-safe screen destinations for Media Sage. */
@Serializable
sealed interface Route : NavKey {

    /** Briefing tab — masthead, date row, and daily figure card. */
    @Serializable
    data object Briefing : Route

    /** Headlines feed — pure news list. */
    @Serializable
    data object Home : Route

    /** Headline detail with matched quote. */
    @Serializable
    data class HeadlineDetail(val articleUrl: String) : Route

    /** Browse voices (figures) collected from reading history. */
    @Serializable
    data object Figures : Route

    /** Detail screen for a specific figure. */
    @Serializable
    data class FigureDetail(val figureId: Long) : Route

    /** You tab — personal content and settings entry point. */
    @Serializable
    data object You : Route

    /** Bookmarks screen — saved matches (shell). */
    @Serializable
    data object Bookmarks : Route

    /** History journal — calendar view of past briefings and readings. */
    @Serializable
    data class History(val epochDay: Long = 0L) : Route

    /** Settings screen (shell). */
    @Serializable
    data object Settings : Route
}

/** Serialization config required for Nav3 on non-JVM platforms. */
val navSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Route.Briefing::class)
        subclass(Route.Home::class)
        subclass(Route.HeadlineDetail::class)
        subclass(Route.Figures::class)
        subclass(Route.FigureDetail::class)
        subclass(Route.You::class)
        subclass(Route.Bookmarks::class)
        subclass(Route.History::class)
        subclass(Route.Settings::class)
    }
}
