package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun toThemeTagsTrimsWhitespaceAndDropsEmptyEntries() {
        val result = " grace , ,faith ".toThemeTags()
        assertEquals(listOf("grace", "faith"), result)
    }

    @Test
    fun toThemeTagsDeduplicatesCaseInsensitivelyKeepingFirstCasingAndOrder() {
        val result = "Grace,grace, GRACE ,faith".toThemeTags()
        assertEquals(listOf("Grace", "faith"), result)
    }

    @Test
    fun toThemeTagsOnBlankStringProducesEmptyList() {
        assertEquals(emptyList(), "   ".toThemeTags())
    }

    @Test
    fun toThemeTagsOnEmptyStringProducesEmptyList() {
        assertEquals(emptyList(), "".toThemeTags())
    }

    @Test
    fun toThemeStringAppliesSameCleaningAndDeduplication() {
        val result = listOf("Grace", "grace", " GRACE ", "faith", "").toThemeString()
        assertEquals("Grace,faith", result)
    }

    @Test
    fun toThemeStringOnEmptyListProducesEmptyString() {
        assertEquals("", emptyList<String>().toThemeString())
    }

    @Test
    fun roundTripIsStableOnSecondConversion() {
        val stored = " Grace, grace ,FAITH,faith ".toThemeTags().toThemeString()
        val roundTripped = stored.toThemeTags().toThemeString()
        assertEquals(stored, roundTripped)
    }
}
