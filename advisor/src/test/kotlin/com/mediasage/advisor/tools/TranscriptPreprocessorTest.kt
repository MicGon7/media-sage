package com.mediasage.advisor.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptPreprocessorTest {

    private val systemEvent = """{"type":"system","subtype":"init","session_id":"abc"}"""
    private val resultEvent = """{"type":"result","subtype":"success"}"""
    private fun assistantEvent(tool: String = "Read") =
        """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","name":"$tool","input":{}}]}}"""
    private fun assistantWithThinking(tool: String = "Bash", thinking: String = "long reasoning") =
        """{"type":"assistant","message":{"role":"assistant","content":""" +
            """[{"type":"thinking","thinking":"$thinking"},{"type":"tool_use","name":"$tool","input":{}}]}}"""
    private fun userEvent(content: String = "file contents") =
        """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","content":"$content"}]}}"""

    @Test
    fun `keeps system and result events`() {
        val transcript = listOf(systemEvent, resultEvent).joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertContains(result, "system")
        assertContains(result, "result")
    }

    @Test
    fun `keeps assistant events`() {
        val transcript = listOf(assistantEvent("Bash"), resultEvent).joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertContains(result, "tool_use")
        assertContains(result, "Bash")
    }

    @Test
    fun `strips thinking blocks from assistant events`() {
        val transcript = listOf(
            assistantWithThinking("Bash", "long reasoning text here"),
            resultEvent,
        ).joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertFalse(result.contains("long reasoning text here"))
        assertContains(result, "tool_use")
        assertContains(result, "Bash")
    }

    @Test
    fun `drops user events`() {
        val transcript = listOf(
            assistantEvent(),
            userEvent("large file dump here"),
            resultEvent,
        ).joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertFalse(result.contains("large file dump here"))
        assertContains(result, "tool_use")
    }

    @Test
    fun `drops malformed json lines`() {
        val transcript = listOf(assistantEvent(), "not json at all", resultEvent).joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertFalse(result.contains("not json at all"))
    }

    @Test
    fun `returns filtered content unchanged when under size threshold`() {
        val transcript = listOf(assistantEvent(), resultEvent).joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertFalse(result.contains("[transcript trimmed for size]"))
    }

    @Test
    fun `trims to head and tail when filtered content exceeds threshold`() {
        val longTool = "x".repeat(200)
        val lines = (1..3000).map { i -> assistantEvent("$i-$longTool") }
        val transcript = lines.joinToString("\n")
        val result = preprocessTranscript(transcript)
        assertTrue(result.contains("[transcript trimmed for size]"))
    }
}
