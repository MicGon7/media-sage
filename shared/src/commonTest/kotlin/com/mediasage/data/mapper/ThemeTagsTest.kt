package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun parseTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), ThemeTags.parse(" grace , ,faith, "))
    }

    @Test
    fun parseDeduplicatesTagsEqualIgnoringCaseAndWhitespaceKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "Faith"), ThemeTags.parse("Grace, grace , FAITH,Faith"))
    }

    @Test
    fun parseOfBlankStringYieldsEmptyList() {
        assertEquals(emptyList(), ThemeTags.parse(""))
        assertEquals(emptyList(), ThemeTags.parse("   "))
    }

    @Test
    fun serializeAppliesSameCleaningAndDeduplicationAsParse() {
        assertEquals("Grace,Faith", ThemeTags.serialize(listOf(" Grace ", "grace", "Faith", " ")))
    }

    @Test
    fun serializeOfEmptyListYieldsEmptyString() {
        assertEquals("", ThemeTags.serialize(emptyList()))
    }

    @Test
    fun roundTripIsStableOnSecondPass() {
        val stored = ThemeTags.serialize(listOf(" Grace ", "grace", "Faith"))
        val parsed = ThemeTags.parse(stored)
        val storedAgain = ThemeTags.serialize(parsed)
        assertEquals(stored, storedAgain)
        assertEquals(parsed, ThemeTags.parse(storedAgain))
    }
}
