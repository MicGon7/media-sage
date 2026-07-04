package com.mediasage.agentruntime

import com.mediasage.agentruntime.service.JiraTicketClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BOT_ACCOUNT_ID = "bot-account-id"

class JiraTicketClientTest {

    @Test
    fun blockerStillInReviewIsDispatchedWhenItIsTheMergedTicket() = runTest {
        val client = buildClient(
            "MS-524" to outwardBlocksJson("MS-525"),
            "MS-525" to candidateJson(BOT_ACCOUNT_ID, listOf("MS-524" to "In Review")),
        )
        assertEquals(listOf("MS-525"), client.getNewlyUnblockedTickets("MS-524"))
    }

    @Test
    fun separatePendingBlockerPreventsDispatch() = runTest {
        val client = buildClient(
            "MS-500" to outwardBlocksJson("MS-525"),
            "MS-525" to candidateJson(BOT_ACCOUNT_ID, listOf("MS-500" to "Done", "MS-501" to "In Review")),
        )
        assertEquals(emptyList(), client.getNewlyUnblockedTickets("MS-500"))
    }

    @Test
    fun allBlockersDoneDispatchesCandidate() = runTest {
        val client = buildClient(
            "MS-500" to outwardBlocksJson("MS-525"),
            "MS-525" to candidateJson(BOT_ACCOUNT_ID, listOf("MS-500" to "Done")),
        )
        assertEquals(listOf("MS-525"), client.getNewlyUnblockedTickets("MS-500"))
    }

    @Test
    fun nonBotAssignedCandidateIsSkipped() = runTest {
        val client = buildClient(
            "MS-500" to outwardBlocksJson("MS-525"),
            "MS-525" to candidateJson("other-account", listOf("MS-500" to "Done")),
        )
        assertEquals(emptyList(), client.getNewlyUnblockedTickets("MS-500"))
    }
}

private fun buildClient(vararg responses: Pair<String, String>): JiraTicketClient {
    val responseMap = responses.toMap()
    val engine = MockEngine { request ->
        val key = request.url.encodedPath.substringAfterLast("/")
        val body = responseMap[key] ?: error("No mock response for Jira key: $key")
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val httpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
    }
    return JiraTicketClient(httpClient, "test-cloud-id", "test@test.com", "token", BOT_ACCOUNT_ID)
}

private fun outwardBlocksJson(vararg blockedKeys: String): String {
    val links = blockedKeys.joinToString(",") { key ->
        """{"type":{"name":"Blocks"},"outwardIssue":{"key":"$key"}}"""
    }
    return """{"fields":{"issuelinks":[$links]}}"""
}

private fun candidateJson(assigneeId: String, inwardBlockers: List<Pair<String, String>>): String {
    val links = inwardBlockers.joinToString(",") { (key, status) ->
        """{"type":{"name":"Blocks"},"inwardIssue":{"key":"$key","fields":{"status":{"name":"$status"}}}}"""
    }
    return """{"fields":{"issuelinks":[$links],"assignee":{"accountId":"$assigneeId"}}}"""
}
