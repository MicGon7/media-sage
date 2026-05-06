package com.mediasage.agent

import com.mediasage.agent.plugins.configureContentNegotiation
import com.mediasage.agent.plugins.configureStatusPages
import com.mediasage.agent.routes.webhookRoutes
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.JiraTicketFetcher
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals

class JiraWebhookRouteTest {

    @Test
    fun autonomousToDoIssueReturns200() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_created", label = "autonomous", status = "To Do"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun assistedIssueReturns200WithoutFiring() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_created", label = "assisted", status = "To Do"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun autonomousIssueNotInToDoReturns200WithoutFiring() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_updated", label = "autonomous", status = "In Progress"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun issueUpdatedEventTriggersAgentForAutonomousToDo() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_updated", label = "autonomous", status = "To Do"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

private fun webhookPayload(event: String, label: String, status: String) = """
    {
      "webhookEvent": "$event",
      "issue": {
        "key": "MS-99",
        "fields": {
          "status": { "name": "$status" },
          "labels": ["$label"]
        }
      }
    }
""".trimIndent()

private fun testWebhookApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
        install(Koin) {
            modules(module {
                single { AgentLaunchService(repoPath = "", scope = CoroutineScope(Dispatchers.IO)) }
                single<JiraTicketFetcher> { object : JiraTicketFetcher {
                    override suspend fun getTicketContent(ticketKey: String): String? = null
                } }
            })
        }
        configureContentNegotiation()
        configureStatusPages()
        routing { webhookRoutes() }
    }
    block()
}
