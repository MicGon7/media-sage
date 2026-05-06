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
    val labels: List<String> = emptyList()
)

@Serializable
data class JiraStatus(
    @SerialName("name")
    val name: String
)

private val relevantEvents = setOf("jira:issue_created", "jira:issue_updated")

/**
 * Jira webhook endpoint. Fires an autonomous Claude Code agent when a ticket
 * with the `autonomous` label enters the To Do status.
 */
fun Route.webhookRoutes() {
    val agentService by inject<AgentLaunchService>()
    val jiraFetcher by inject<JiraTicketFetcher>()

    post("/webhook/jira") {
        val payload = call.receive<JiraWebhookPayload>()
        val fields = payload.issue.fields

        val shouldFire = payload.webhookEvent in relevantEvents &&
            "autonomous" in fields.labels &&
            fields.status.name == "To Do"

        if (shouldFire) {
            val ticketContent = jiraFetcher.getTicketContent(payload.issue.key)
            agentService.launch(payload.issue.key, ticketContent)
        }

        call.respond(HttpStatusCode.OK)
    }
}
