package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun toThemeListTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), " grace , ,faith ,  ".toThemeList())
    }

    @Test
    fun toThemeListCollapsesTagsEqualIgnoringCaseAndWhitespaceKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "faith"), "Grace,faith,grace, GRACE ".toThemeList())
    }

    @Test
    fun toThemeListHandlesEmptyString() {
        assertEquals(emptyList(), "".toThemeList())
    }

    @Test
    fun toThemeListHandlesBlankString() {
        assertEquals(emptyList(), "   ".toThemeList())
    }

    @Test
    fun toThemeStringAppliesSameCleaningAndDeduplicationAsToThemeList() {
        assertEquals("Grace,faith", listOf(" Grace ", "faith", "grace", " GRACE ").toThemeString())
    }

    @Test
    fun toThemeStringOfEmptyListIsEmptyString() {
        assertEquals("", emptyList<String>().toThemeString())
    }

    @Test
    fun roundTripIsStableOnSecondPass() {
        val firstRead = " Grace , grace,faith ".toThemeList()
        val stored = firstRead.toThemeString()
        val secondRead = stored.toThemeList()
        assertEquals(firstRead, secondRead)
        assertEquals(stored, secondRead.toThemeString())
    }
}
