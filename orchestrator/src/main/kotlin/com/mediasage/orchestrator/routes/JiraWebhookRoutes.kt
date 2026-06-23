package com.mediasage.orchestrator.routes

import com.mediasage.orchestrator.service.AgentLauncher
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

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
 * Registers the Jira webhook route (`POST /webhook/jira`) on this [Route].
 *
 * **Expected payload shape:**
 * The endpoint accepts a JSON body matching [JiraWebhookPayload], which Atlassian sends
 * for `jira:issue_created` and `jira:issue_updated` events. The relevant fields are:
 * - `webhookEvent`: event type string (e.g. `"jira:issue_updated"`)
 * - `issue.key`: Jira ticket key (e.g. `"MS-123"`)
 * - `issue.fields.status.name`: current status of the issue
 * - `issue.fields.assignee.accountId`: Atlassian account ID of the assignee
 * - `issue.fields.labels`: labels attached to the issue
 *
 * **Dispatch behavior:**
 * A Cloud Run Job is dispatched when all three conditions are satisfied:
 * 1. `webhookEvent` is `"jira:issue_created"` or `"jira:issue_updated"`.
 * 2. The issue assignee matches [botAccountId] (the autonomous bot account).
 * 3. The issue status is `"In Progress"`.
 *
 * When dispatched, the route calls [AgentLauncher.launch] with the ticket key. The worker
 * fetches all ticket content from Jira at runtime. The response is always
 * `200 OK` — failures surface via logs and dedup state in Supabase rather than HTTP
 * error codes, preventing Jira from retrying on transient errors.
 *
 * An optional `X-Dry-Run: true` request header runs only the dedup check and row
 * insertion without dispatching a Cloud Run Job, useful for smoke-testing the pipeline.
 *
 * @param botAccountId Atlassian account ID of the autonomous bot. Only issues assigned
 *   to this account trigger a dispatch.
 */
fun Route.webhookRoutes(botAccountId: String) {
    val log = LoggerFactory.getLogger("JiraWebhookRoutes")
    val agentService by inject<AgentLauncher>()

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
            val dryRun = call.request.headers["X-Dry-Run"]?.lowercase() == "true"
            if (dryRun) log.info("Dry-run mode — dedup check and row insert only, Cloud Run dispatch skipped")
            agentService.launch(payload.issue.key, dryRun)
        }

        call.respond(HttpStatusCode.OK)
    }
}
