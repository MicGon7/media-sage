package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun toThemeTagsTrimsSurroundingWhitespace() {
        assertEquals(listOf("grace", "faith"), " grace , faith ".toThemeTags())
    }

    @Test
    fun toThemeTagsRemovesEmptyEntries() {
        assertEquals(listOf("grace", "faith"), "grace,,faith,".toThemeTags())
    }

    @Test
    fun toThemeTagsDeduplicatesIgnoringCaseAndWhitespaceKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "Faith"), "Grace, grace ,Faith,FAITH".toThemeTags())
    }

    @Test
    fun toThemeTagsOnBlankStringYieldsEmptyList() {
        assertEquals(emptyList(), "   ".toThemeTags())
    }

    @Test
    fun toThemeTagsOnEmptyStringYieldsEmptyList() {
        assertEquals(emptyList(), "".toThemeTags())
    }

    @Test
    fun toThemeTagsStringAppliesSameCleaningAndDeduplication() {
        assertEquals("Grace,Faith", listOf(" Grace ", "grace", "Faith", " FAITH").toThemeTagsString())
    }

    @Test
    fun toThemeTagsStringOnEmptyListYieldsEmptyString() {
        assertEquals("", emptyList<String>().toThemeTagsString())
    }

    @Test
    fun roundTripIsStableAfterSecondConversion() {
        val stored = " Grace, grace ,Faith,,faith"
        val firstPass = stored.toThemeTags().toThemeTagsString()
        val secondPass = firstPass.toThemeTags().toThemeTagsString()
        assertEquals(firstPass, secondPass)
    }
}
