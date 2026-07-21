package com.mediasage.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTagsTest {

    @Test
    fun fromStoredTrimsWhitespaceAndDropsEmptyEntries() {
        assertEquals(listOf("grace", "faith"), themeTagsFromStored(" grace ,, faith ,  "))
    }

    @Test
    fun fromStoredDedupesCaseAndWhitespaceInsensitivelyKeepingFirstCasingAndOrder() {
        assertEquals(
            listOf("Grace", "Faith"),
            themeTagsFromStored("Grace, grace, GRACE, Faith, faith")
        )
    }

    @Test
    fun fromStoredHandlesBlankOrEmptyStringAsEmptyList() {
        assertEquals(emptyList(), themeTagsFromStored(""))
        assertEquals(emptyList(), themeTagsFromStored("   "))
    }

    @Test
    fun toStoredCleansAndDedupesBeforeJoining() {
        assertEquals("Grace,Faith", themeTagsToStored(listOf(" Grace ", "grace", "Faith", "")))
    }

    @Test
    fun toStoredOfEmptyListIsEmptyString() {
        assertEquals("", themeTagsToStored(emptyList()))
    }

    @Test
    fun roundTripIsStableOnSecondPass() {
        val stored = themeTagsToStored(listOf(" Grace ", "GRACE", "Faith"))
        val tags = themeTagsFromStored(stored)
        val storedAgain = themeTagsToStored(tags)
        assertEquals(stored, storedAgain)
        assertEquals(tags, themeTagsFromStored(storedAgain))
    }
}
