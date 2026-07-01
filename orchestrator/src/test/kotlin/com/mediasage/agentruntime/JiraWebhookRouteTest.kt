package com.mediasage.agentruntime

import com.mediasage.agentruntime.plugins.configureContentNegotiation
import com.mediasage.agentruntime.plugins.configureStatusPages
import com.mediasage.agentruntime.routes.webhookRoutes
import com.mediasage.agentruntime.service.AgentLaunchService
import com.mediasage.agentruntime.service.AgentLauncher
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

private const val BOT_ACCOUNT_ID = "bot-account-id"

class JiraWebhookRouteTest {

    @Test
    fun botAssigneeInProgressFires() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_created", assigneeAccountId = BOT_ACCOUNT_ID, status = "In Progress"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun wrongAssigneeInProgressDoesNotFire() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_created", assigneeAccountId = "other-account-id", status = "In Progress"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun noAssigneeInProgressDoesNotFire() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_created", assigneeAccountId = null, status = "In Progress"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun botAssigneeToDoDoesNotFire() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_created", assigneeAccountId = BOT_ACCOUNT_ID, status = "To Do"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun issueUpdatedEventFires() = testWebhookApp {
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_updated", assigneeAccountId = BOT_ACCOUNT_ID, status = "In Progress"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

}

private fun webhookPayload(event: String, assigneeAccountId: String?, status: String) = """
{
  "webhookEvent": "$event",
  "issue": {
    "key": "MS-99",
    "fields": {
      "status": { "name": "$status" },
      "labels": [],
      ${if (assigneeAccountId != null) "\"assignee\": { \"accountId\": \"$assigneeAccountId\" }" else "\"assignee\": null"}
    }
  }
}
""".trimIndent()

private fun testWebhookApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
        install(Koin) {
            modules(module {
                single { AgentLaunchService(scope = CoroutineScope(Dispatchers.IO)) }
                single<AgentLauncher> { get<AgentLaunchService>() }
            })
        }
        configureContentNegotiation()
        configureStatusPages()
        routing { webhookRoutes(BOT_ACCOUNT_ID) }
    }
    block()
}
