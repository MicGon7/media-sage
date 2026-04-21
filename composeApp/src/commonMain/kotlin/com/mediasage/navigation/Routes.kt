package com.mediasage.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Type-safe screen destinations for Media Sage. */
@Serializable
sealed interface Route : NavKey {

    /** Headlines feed — the main screen. */
    @Serializable
    data object Home : Route

    /** Quote match for a specific headline. */
    @Serializable
    data class Match(val headlineId: Long) : Route

    /** Browse and select figures for matching. */
    @Serializable
    data object Figures : Route
}

/** Serialization config required for Nav3 on non-JVM platforms. */
val navSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Route.Home::class)
        subclass(Route.Match::class)
        subclass(Route.Figures::class)
    }
}
