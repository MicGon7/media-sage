package com.mediasage.agentruntime

import com.mediasage.agentruntime.service.SlackApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlackApiClientTest {

    @Test
    fun postsTextPayloadToWebhookUrl() = runTest {
        var capturedUrl: String? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedBody = (request.body as TextContent).text
            respond("ok", HttpStatusCode.OK)
        }
        val client = SlackApiClient(HttpClient(engine), "https://hooks.slack.com/services/T/B/xyz")

        client.send("hello world")

        val payload = capturedBody!!
        assertEquals("https://hooks.slack.com/services/T/B/xyz", capturedUrl)
        assertTrue(payload.contains("\"text\""), "payload uses the Slack text field")
        assertTrue(payload.contains("hello world"), "payload carries the message text")
    }

    @Test
    fun blankWebhookUrlIsNoOp() = runTest {
        var called = false
        val engine = MockEngine {
            called = true
            respond("ok", HttpStatusCode.OK)
        }
        val client = SlackApiClient(HttpClient(engine), "")

        client.send("should not send")

        assertFalse(called, "no HTTP call is made when the webhook URL is blank")
    }
}
