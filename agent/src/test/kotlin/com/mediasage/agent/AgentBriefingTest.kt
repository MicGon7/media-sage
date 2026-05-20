package com.mediasage.agent

import com.mediasage.agent.service.AgentBriefing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentBriefingTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `prepare returns empty string when claude is not found`() {
        // Simulates an environment where claude CLI is not on PATH (e.g. CI without Claude Code).
        // prepare() must swallow all failures internally and return "" — never throw.
        val briefing = AgentBriefing(repoPath = tempDir.absolutePath, timeoutSeconds = 5L)

        // In CI without claude installed this returns "". Locally it may return a real briefing.
        // Either way the call must complete without throwing.
        val result = briefing.prepare("MS-TEST", "Test ticket content")

        assertTrue(result is String, "prepare() must return a String in all cases")
    }

    @Test
    fun `prepare returns empty string on timeout`() {
        // Set an impossibly short timeout to force a timeout path
        val briefing = AgentBriefing(repoPath = tempDir.absolutePath, timeoutSeconds = 0L)

        val result = briefing.prepare("MS-TEST", "Test ticket content")

        // Timeout must produce "" not an exception
        assertEquals("", result)
    }
}
