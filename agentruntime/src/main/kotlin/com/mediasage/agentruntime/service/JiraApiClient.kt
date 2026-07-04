package com.mediasage.agentruntime.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import org.slf4j.LoggerFactory


@Serializable
private data class JiraIssueStatusResponse(
    @SerialName("fields")
    val fields: JiraStatusFields? = null
)

@Serializable
private data class JiraStatusFields(
    @SerialName("status")
    val status: JiraStatusName? = null
)

@Serializable
private data class JiraStatusName(
    @SerialName("name")
    val name: String = ""
)

@Serializable
private data class JiraIssueContentResponse(
    @SerialName("fields")
    val fields: JiraContentFields? = null
)

@Serializable
private data class JiraContentFields(
    @SerialName("summary")
    val summary: String = "",
    @SerialName("description")
    val description: JsonElement? = null
)

@Serializable
private data class JiraTransitionsResponse(
    @SerialName("transitions")
    val transitions: List<JiraTransition> = emptyList()
)

@Serializable
private data class JiraTransition(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
)

private const val IN_PROGRESS_STATUS = "In Progress"

/**
 * Jira Cloud REST API v3 client that implements label checking, ticket content fetching,
 * status inspection, and comment posting for the Media Sage agent orchestrator.
 *
 * All methods authenticate with HTTP Basic auth derived from [email] and [apiToken].
 * Failures are logged at WARN level and returned as null or false rather than thrown,
 * so callers can treat a missing response as a safe no-op.
 *
 * @param httpClient Ktor HTTP client used for all Jira API requests.
 * @param cloudId Atlassian Cloud instance ID (UUID in the `api.atlassian.com` URL path).
 * @param email Email address of the Atlassian account used for Basic auth.
 * @param apiToken Atlassian API token paired with [email].
 */
open class JiraApiClient(
    protected val httpClient: HttpClient,
    cloudId: String,
    email: String,
    apiToken: String,
) {

    private val log = LoggerFactory.getLogger(JiraApiClient::class.java)
    protected val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("$email:$apiToken".toByteArray(Charsets.UTF_8))

    protected val baseUrl = "https://api.atlassian.com/ex/jira/$cloudId/rest/api/3"

    /**
     * Returns the summary and description of [ticketKey] as a formatted string, or null if
     * the ticket cannot be retrieved.
     *
     * Calls `GET /rest/api/3/issue/{ticketKey}?fields=summary,description`.
     *
     * The description is extracted from Atlassian Document Format (ADF) by recursively
     * collecting all `text` leaf nodes. The returned string has the form:
     * `"**KEY: Summary**\n\n<description text>"`.
     *
     * @param ticketKey Jira issue key (e.g. `MS-242`).
     * @return Formatted ticket content string, or null on HTTP error or network failure.
     */
    open suspend fun getTicketContent(ticketKey: String): String? {
        return try {
            val response = httpClient.get("$baseUrl/issue/$ticketKey?fields=summary,description") {
                header(HttpHeaders.Authorization, authHeader)
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                log.warn("Jira API returned ${response.status} for $ticketKey content fetch")
                return null
            }
            val body = response.body<JiraIssueContentResponse>()
            val fields = body.fields ?: run {
                log.warn("Jira response for $ticketKey had no fields")
                return null
            }
            val description = fields.description?.let { extractText(it) }.orEmpty()
            "**$ticketKey: ${fields.summary}**\n\n$description"
        } catch (e: Exception) {
            log.warn("Failed to fetch ticket content for $ticketKey: ${e.message}")
            null
        }
    }

    /**
     * Returns the workflow status name of [ticketKey] (e.g. `"In Progress"`), or null if
     * the ticket cannot be retrieved.
     *
     * Calls `GET /rest/api/3/issue/{ticketKey}?fields=status`.
     *
     * @param ticketKey Jira issue key (e.g. `MS-242`).
     * @return Status name as configured in the Jira workflow, or null on HTTP error or
     *   network failure.
     */
    open suspend fun getTicketStatus(ticketKey: String): String? {
        return try {
            val response = httpClient.get("$baseUrl/issue/$ticketKey?fields=status") {
                header(HttpHeaders.Authorization, authHeader)
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                log.warn("Jira API returned ${response.status} for $ticketKey status fetch")
                return null
            }
            val body = response.body<JiraIssueStatusResponse>()
            body.fields?.status?.name
        } catch (e: Exception) {
            log.warn("Failed to fetch ticket status for $ticketKey: ${e.message}")
            null
        }
    }

    /**
     * Posts [body] as a plain-text comment on [ticketKey] using the Jira ADF comment payload.
     *
     * Calls `POST /rest/api/3/issue/{ticketKey}/comment`. The body is wrapped in an
     * Atlassian Document Format (ADF) `doc` → `paragraph` → `text` structure before sending.
     *
     * Failures are logged at WARN level and swallowed — callers do not need to handle
     * exceptions.
     *
     * @param ticketKey Jira issue key (e.g. `MS-242`).
     * @param body Comment text to post.
     */
    /**
     * Transitions [ticketKey] to "In Progress" by fetching available transitions dynamically
     * and applying the one named "In Progress". Failures are logged and swallowed.
     *
     * Calls `GET /rest/api/3/issue/{ticketKey}/transitions` then
     * `POST /rest/api/3/issue/{ticketKey}/transitions`.
     *
     * @param ticketKey Jira issue key (e.g. `MS-531`).
     */
    open suspend fun transitionToInProgress(ticketKey: String) {
        try {
            val fetchResponse = httpClient.get("$baseUrl/issue/$ticketKey/transitions") {
                header(HttpHeaders.Authorization, authHeader)
                accept(ContentType.Application.Json)
            }
            if (!fetchResponse.status.isSuccess()) {
                log.warn("[$ticketKey] Failed to fetch Jira transitions: ${fetchResponse.status}")
                return
            }
            val body = fetchResponse.body<JiraTransitionsResponse>()
            val transitionId = body.transitions.find { it.name == IN_PROGRESS_STATUS }?.id ?: run {
                log.warn("[$ticketKey] No '$IN_PROGRESS_STATUS' transition found in Jira")
                return
            }
            val postResponse = httpClient.post("$baseUrl/issue/$ticketKey/transitions") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody("""{"transition":{"id":"$transitionId"}}""")
            }
            if (!postResponse.status.isSuccess()) {
                log.warn("[$ticketKey] Failed to apply In Progress transition: ${postResponse.status}")
            }
        } catch (e: Exception) {
            log.warn("[$ticketKey] Failed to transition to In Progress: ${e.message}")
        }
    }

    open suspend fun addComment(ticketKey: String, body: String) {
        try {
            val escapedBody = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonPrimitive(body)
            )
            val payload = buildJiraCommentPayload(escapedBody)
            val response = httpClient.post("$baseUrl/issue/$ticketKey/comment") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) {
                log.warn("[$ticketKey] Failed to post Jira comment: ${response.status} — ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            log.warn("[$ticketKey] Failed to post Jira comment: ${e.message}")
        }
    }

    private fun extractText(element: JsonElement): String = when (element) {
        is JsonObject -> {
            val text = element["text"]?.jsonPrimitive?.content
            val content = element["content"]
            when {
                text != null -> text
                content is JsonArray -> content.joinToString("\n") { extractText(it) }
                else -> ""
            }
        }
        is JsonArray -> element.joinToString("\n") { extractText(it) }
        else -> ""
    }
}

private fun buildJiraCommentPayload(escapedBody: String) =
    """{"body":{"version":1,"type":"doc","content":""" +
        """[{"type":"paragraph","content":[{"type":"text","text":$escapedBody}]}]}}"""
