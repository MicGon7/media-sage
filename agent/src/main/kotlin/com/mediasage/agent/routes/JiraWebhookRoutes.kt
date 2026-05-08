package com.mediasage.agent.routes

import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.JiraTicketFetcher
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.logging.Logger

// ---- Jira webhook payload DTOs ----

@Serializable
data class JiraWebhookPayload(
    @SerialName("webhookEvent")
    val webhookEvent: String,
    @SerialName("issue")
    val issue: JiraIssue
)

@Serializable
data class JiraIssue(
    @SerialName("key")
    val key: String,
    @SerialName("fields")
    val fields: JiraIssueFields
)

@Serializable
data class JiraIssueFields(
    @SerialName("status")
    val status: JiraStatus,
    @SerialName("labels")
    val labels: List<String> = emptyList(),
    @SerialName("assignee")
    val assignee: JiraAssignee? = null
)

@Serializable
data class JiraStatus(
    @SerialName("name")
    val name: String
)

@Serializable
data class JiraAssignee(
    @SerialName("accountId")
    val accountId: String
)

private val relevantEvents = setOf("jira:issue_created", "jira:issue_updated")

/**
 * Jira webhook endpoint. Fires an autonomous Claude Code agent when a ticket
 * assigned to the bot account transitions to In Progress.
 */
fun Route.webhookRoutes(botAccountId: String) {
    val log = Logger.getLogger("JiraWebhookRoutes")
    val agentService by inject<AgentLaunchService>()
    val jiraFetcher by inject<JiraTicketFetcher>()

    post("/webhook/jira") {
        val payload = call.receive<JiraWebhookPayload>()
        val fields = payload.issue.fields

        log.info(
            "Jira webhook: event=${payload.webhookEvent} key=${payload.issue.key} " +
            "status='${fields.status.name}' assignee=${fields.assignee?.accountId}"
        )

        val shouldFire = payload.webhookEvent in relevantEvents &&
            fields.assignee?.accountId == botAccountId &&
            fields.status.name == "In Progress"

        if (shouldFire) {
            val ticketContent = jiraFetcher.getTicketContent(payload.issue.key)
            agentService.launch(payload.issue.key, ticketContent)
        }

        call.respond(HttpStatusCode.OK)
    }
}
