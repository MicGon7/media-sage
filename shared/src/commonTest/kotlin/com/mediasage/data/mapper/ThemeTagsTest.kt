package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun parseTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), parseThemeTags(" grace , faith ,, "))
    }

    @Test
    fun parseDeduplicatesCaseAndWhitespaceInsensitivelyKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "faith"), parseThemeTags("Grace,faith,grace, GRACE "))
    }

    @Test
    fun parseBlankStoredStringYieldsEmptyList() {
        assertEquals(emptyList(), parseThemeTags(""))
        assertEquals(emptyList(), parseThemeTags("   "))
    }

    @Test
    fun formatCleansAndDeduplicatesBeforeJoining() {
        assertEquals("Grace,faith", formatThemeTags(listOf(" Grace ", "faith", "grace", " GRACE")))
    }

    @Test
    fun formatEmptyListYieldsEmptyStoredString() {
        assertEquals("", formatThemeTags(emptyList()))
    }

    @Test
    fun roundTripIsStableOnceStored() {
        val stored = formatThemeTags(listOf(" Grace ", "grace", "Faith"))
        assertEquals(stored, formatThemeTags(parseThemeTags(stored)))
    }
}
