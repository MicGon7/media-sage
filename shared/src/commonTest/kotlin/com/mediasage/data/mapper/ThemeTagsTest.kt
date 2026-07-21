package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun parseTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), parseThemeTags(" grace , , faith ,  "))
    }

    @Test
    fun parseDeduplicatesCaseInsensitivelyKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "faith"), parseThemeTags("Grace, grace, faith, GRACE"))
    }

    @Test
    fun parseBlankOrEmptyStringYieldsEmptyList() {
        assertEquals(emptyList(), parseThemeTags(""))
        assertEquals(emptyList(), parseThemeTags("   "))
    }

    @Test
    fun formatAppliesSameCleaningAndDeduplication() {
        assertEquals("Grace,faith", formatThemeTags(listOf(" Grace ", "grace", "faith", "")))
    }

    @Test
    fun formatEmptyListYieldsEmptyString() {
        assertEquals("", formatThemeTags(emptyList()))
    }

    @Test
    fun roundTripIsStableOnSecondPass() {
        val stored = formatThemeTags(listOf(" Grace ", "grace", "faith"))
        val readBack = parseThemeTags(stored)
        assertEquals(stored, formatThemeTags(readBack))
    }
}
