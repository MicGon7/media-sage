package com.mediasage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun toThemeTagListTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(
            listOf("grace", "faith", "redemption"),
            " grace , faith ,, redemption ,  ".toThemeTagList()
        )
    }

    @Test
    fun toThemeTagListDeduplicatesCaseInsensitivelyKeepingFirstCasingAndOrder() {
        assertEquals(
            listOf("Grace", "faith"),
            "Grace, grace , GRACE,faith,Faith".toThemeTagList()
        )
    }

    @Test
    fun toThemeTagStringAppliesSameCleaningAndDeduplication() {
        assertEquals(
            "Grace,faith",
            listOf(" Grace ", "grace", "faith", "FAITH", "").toThemeTagString()
        )
    }

    @Test
    fun roundTripIsStableOnSecondPass() {
        val stored = " Grace , grace ,faith,,Faith ".toThemeTagList().toThemeTagString()
        assertEquals(stored, stored.toThemeTagList().toThemeTagString())
    }

    @Test
    fun blankStoredStringProducesEmptyList() {
        assertEquals(emptyList(), "   ".toThemeTagList())
        assertEquals(emptyList(), "".toThemeTagList())
    }

    @Test
    fun emptyListProducesEmptyStoredString() {
        assertEquals("", emptyList<String>().toThemeTagString())
    }
}
