package com.mediasage.orchestrator

import com.mediasage.orchestrator.service.parseStreamJsonMilestone
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamJsonParserTest {

    @Test
    fun systemInitEmitsModelName() {
        val line = """{"type":"system","subtype":"init","model":"claude-sonnet-4-6","session_id":"abc"}"""
        val result = parseStreamJsonMilestone(line)
        assertTrue(result?.contains("claude-sonnet-4-6") == true, "Expected model name in: $result")
    }

    @Test
    fun assistantToolUseEmitsToolNameAndCommand() {
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[
              {"type":"tool_use","id":"t1","name":"Bash","input":{"command":"./gradlew :agent:test","description":"Run tests"}}
            ]}}
        """.trimIndent()
        val result = parseStreamJsonMilestone(line)
        assertTrue(result?.contains("Bash") == true, "Expected tool name in: $result")
        assertTrue(result?.contains("./gradlew :agent:test") == true, "Expected command in: $result")
    }

    @Test
    fun assistantTextBlockEmitsThinkingPrefix() {
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[
              {"type":"text","text":"Let me check the failing test output before making changes."}
            ]}}
        """.trimIndent()
        val result = parseStreamJsonMilestone(line)
        assertTrue(result?.startsWith("thinking:") == true, "Expected thinking prefix in: $result")
    }

    @Test
    fun assistantBlankTextIsSuppressed() {
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[
              {"type":"text","text":"   "}
            ]}}
        """.trimIndent()
        assertNull(parseStreamJsonMilestone(line))
    }

    @Test
    fun userToolResultErrorEmitsToolError() {
        val line = """
            {"type":"user","message":{"role":"user","content":[
              {"type":"tool_result","tool_use_id":"t1","is_error":true,"content":"exit code 1: build failed"}
            ]}}
        """.trimIndent()
        val result = parseStreamJsonMilestone(line)
        assertTrue(result?.contains("tool error") == true, "Expected tool error in: $result")
        assertTrue(result?.contains("exit code 1") == true, "Expected error detail in: $result")
    }

    @Test
    fun userToolResultSuccessIsSuppressed() {
        val line = """
            {"type":"user","message":{"role":"user","content":[
              {"type":"tool_result","tool_use_id":"t1","is_error":false,"content":"BUILD SUCCESSFUL"}
            ]}}
        """.trimIndent()
        assertNull(parseStreamJsonMilestone(line))
    }

    @Test
    fun resultSuccessEmitsDoneWithDurationAndCost() {
        val line = """{"type":"result","subtype":"success","duration_ms":12345,"cost_usd":0.0123,"session_id":"abc"}"""
        val result = parseStreamJsonMilestone(line)
        assertTrue(result?.contains("done") == true, "Expected done in: $result")
        assertTrue(result?.contains("success") == true, "Expected subtype in: $result")
        assertTrue(result?.contains("12345ms") == true, "Expected duration in: $result")
        assertTrue(result?.contains("0.0123") == true, "Expected cost in: $result")
    }

    @Test
    fun tokenEventIsSuppressed() {
        val line = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Le"}}"""
        assertNull(parseStreamJsonMilestone(line))
    }

    @Test
    fun unknownTypeIsSuppressed() {
        val line = """{"type":"ping","timestamp":1234567890}"""
        assertNull(parseStreamJsonMilestone(line))
    }

    @Test
    fun invalidJsonIsSuppressed() {
        assertNull(parseStreamJsonMilestone("not json at all"))
        assertNull(parseStreamJsonMilestone(""))
        assertNull(parseStreamJsonMilestone("{broken"))
    }

    @Test
    fun longCommandIsTruncatedTo120Chars() {
        val longCommand = "x".repeat(200)
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[
              {"type":"tool_use","id":"t1","name":"Bash","input":{"command":"$longCommand"}}
            ]}}
        """.trimIndent()
        val result = parseStreamJsonMilestone(line)
        assertTrue((result?.length ?: 0) < 200, "Expected truncation but got full length")
    }

    @Test
    fun multipleContentBlocksEmitMultipleLines() {
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[
              {"type":"text","text":"First I will list files."},
              {"type":"tool_use","id":"t1","name":"Bash","input":{"command":"ls"}}
            ]}}
        """.trimIndent()
        val result = parseStreamJsonMilestone(line)
        assertTrue(result?.contains("thinking:") == true, "Expected thinking line")
        assertTrue(result?.contains("tool: Bash") == true, "Expected tool line")
    }
}
