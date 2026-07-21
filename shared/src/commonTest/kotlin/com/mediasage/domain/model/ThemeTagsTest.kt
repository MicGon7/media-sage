package com.mediasage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun fromStorageTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), ThemeTags.fromStorage(" grace ,faith, ,"))
    }

    @Test
    fun fromStorageDeduplicatesCaseAndWhitespaceInsensitiveKeepingFirstCasingAndOrder() {
        assertEquals(listOf("Grace", "faith"), ThemeTags.fromStorage("Grace,faith,grace, GRACE "))
    }

    @Test
    fun fromStorageHandlesEmptyOrBlankString() {
        assertEquals(emptyList(), ThemeTags.fromStorage(""))
        assertEquals(emptyList(), ThemeTags.fromStorage("   "))
    }

    @Test
    fun toStorageAppliesSameCleaningAndDeduplication() {
        assertEquals("Grace,faith", ThemeTags.toStorage(listOf(" Grace ", "faith", "grace", "")))
    }

    @Test
    fun toStorageHandlesEmptyList() {
        assertEquals("", ThemeTags.toStorage(emptyList()))
    }

    @Test
    fun roundTripIsStableOnSecondPass() {
        val stored = ThemeTags.toStorage(listOf(" Grace ", "grace", "Faith", " faith "))
        val tags = ThemeTags.fromStorage(stored)
        assertEquals(stored, ThemeTags.toStorage(tags))
        assertEquals(tags, ThemeTags.fromStorage(stored))
    }
}
