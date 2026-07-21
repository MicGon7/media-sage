package com.mediasage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun toListTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), ThemeTags.toList(" grace , faith ,  , "))
    }

    @Test
    fun toListDedupesCaseAndWhitespaceInsensitivelyKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "faith"), ThemeTags.toList("Grace,faith,grace, GRACE ,Faith"))
    }

    @Test
    fun toStoredAppliesSameCleaningAndDeduplication() {
        assertEquals("Grace,faith", ThemeTags.toStored(listOf(" Grace ", "faith", "grace", "FAITH")))
    }

    @Test
    fun roundTripIsStable() {
        val stored = ThemeTags.toStored(listOf(" Grace ", "faith", "grace", "", "Faith "))
        assertEquals(stored, ThemeTags.toStored(ThemeTags.toList(stored)))
    }

    @Test
    fun blankStoredStringProducesEmptyList() {
        assertEquals(emptyList(), ThemeTags.toList("   "))
        assertEquals(emptyList(), ThemeTags.toList(""))
    }

    @Test
    fun emptyListProducesEmptyStoredString() {
        assertEquals("", ThemeTags.toStored(emptyList()))
    }
}
