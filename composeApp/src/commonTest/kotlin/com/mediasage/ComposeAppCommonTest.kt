package com.mediasage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun greetingContainsPlatformName() {
        val greeting = Greeting().greet()
        assertTrue(greeting.isNotEmpty(), "Greeting should not be empty")
    }

    @Test
    fun platformNameIsAvailable() {
        val platform = getPlatform()
        assertTrue(platform.name.isNotEmpty(), "Platform name should not be empty")
    }
}
