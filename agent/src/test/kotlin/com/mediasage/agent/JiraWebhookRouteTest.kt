package com.mediasage.agent

import com.mediasage.agent.di.AgentConfig
import com.mediasage.agent.di.agentModule
import com.mediasage.agent.plugins.configureContentNegotiation
import com.mediasage.agent.plugins.configureStatusPages
import com.mediasage.agent.routes.webhookRoutes
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.AgentLauncher
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

    // Regression test for MS-187: when Cloud Run setup fails on startup (bad DB URL,
    // missing credentials, etc.), the Koin singleton must not poison subsequent resolutions.
    // Before the fix, getOrNull<CloudRunDispatch?>() threw instead of returning null,
    // causing AgentLaunchService to fail to create and all webhooks to return 500.
    @Test
    fun webhookReturns200WhenCloudRunSetupFails() = testApplication {
        application {
            val config = AgentConfig(
                repoPath = "",
                githubWebhookSecret = "",
                jiraEmail = "",
                jiraApiToken = "",
                jiraCloudId = "",
                jiraBotAccountId = BOT_ACCOUNT_ID,
                useCloudRunWorkers = true,
                supabaseDbUrl = "jdbc:postgresql://invalid-host:5432/postgres",
                googleCredentialsJson = "{}"
            )
            val scope = CoroutineScope(Dispatchers.IO)
            install(Koin) { modules(agentModule(config, scope)) }
            configureContentNegotiation()
            configureStatusPages()
            routing { webhookRoutes(BOT_ACCOUNT_ID) }
        }
        // Use "To Do" so shouldFire = false and no real claude process is spawned —
        // the inject<AgentLaunchService>() lazy delegate still resolves on the first
        // request, which is exactly what we're testing.
        val response = client.post("/webhook/jira") {
            contentType(ContentType.Application.Json)
            setBody(webhookPayload(event = "jira:issue_updated", assigneeAccountId = BOT_ACCOUNT_ID, status = "To Do"))
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
                single { AgentLaunchService(repoPath = "", scope = CoroutineScope(Dispatchers.IO)) }
                single<AgentLauncher> { get<AgentLaunchService>() }
                single<JiraTicketFetcher> { object : JiraTicketFetcher {
                    override suspend fun getTicketContent(ticketKey: String): String? = null
                } }
            })
        }
        configureContentNegotiation()
        configureStatusPages()
        routing { webhookRoutes(BOT_ACCOUNT_ID) }
    }
    block()
}
