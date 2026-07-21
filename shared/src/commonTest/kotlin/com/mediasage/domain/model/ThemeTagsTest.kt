package com.mediasage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun parseTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(
            listOf("grace", "faith", "redemption"),
            ThemeTags.parse(" grace ,faith,  redemption  ,, ")
        )
    }

    @Test
    fun parseDeduplicatesCaseAndWhitespaceVariantsKeepingFirstCasingAndOrder() {
        assertEquals(
            listOf("Grace", "faith"),
            ThemeTags.parse("Grace,grace, grace ,faith,GRACE")
        )
    }

    @Test
    fun parseOnEmptyOrBlankStringYieldsEmptyList() {
        assertEquals(emptyList(), ThemeTags.parse(""))
        assertEquals(emptyList(), ThemeTags.parse("   "))
    }

    @Test
    fun formatOnEmptyListYieldsEmptyString() {
        assertEquals("", ThemeTags.format(emptyList()))
    }

    @Test
    fun formatAppliesSameCleaningAndDeduplicationAsParse() {
        assertEquals(
            "Grace,faith",
            ThemeTags.format(listOf(" Grace ", "grace", "faith", "", "FAITH"))
        )
    }

    @Test
    fun roundTripIsStableAfterFirstNormalization() {
        val stored = ThemeTags.format(listOf(" Grace ", "grace", "faith", "FAITH"))
        val parsedAgain = ThemeTags.parse(stored)
        val storedAgain = ThemeTags.format(parsedAgain)
        assertEquals(stored, storedAgain)
        assertEquals(listOf("Grace", "faith"), parsedAgain)
    }
}
