package com.mediasage.agent.service

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

/** Fetches the human-readable content of a Jira ticket. */
interface JiraTicketFetcher {
    /**
     * Returns the summary and description of [ticketKey] as a formatted string, or null if
     * the ticket cannot be retrieved.
     *
     * @param ticketKey Jira issue key (e.g. `MS-242`).
     * @return Markdown-ish string starting with `**KEY: Summary**` followed by description
     *   text, or null on HTTP error or network failure.
     */
    suspend fun getTicketContent(ticketKey: String): String?
}

/** Posts a comment on a Jira ticket. */
interface JiraCommentPoster {
    /**
     * Adds [body] as a plain-text comment on [ticketKey] using the Jira Cloud REST API v3.
     *
     * Failures are logged and swallowed — callers do not need to handle exceptions.
     *
     * @param ticketKey Jira issue key (e.g. `MS-242`).
     * @param body Comment text to post.
     */
    suspend fun addComment(ticketKey: String, body: String)
}

/** Retrieves the workflow status of a Jira ticket. */
interface JiraTicketStatusChecker {
    /**
     * Returns the status name of [ticketKey] (e.g. `"In Progress"`), or null if the ticket
     * cannot be retrieved.
     *
     * @param ticketKey Jira issue key (e.g. `MS-242`).
     * @return Status name string as configured in the Jira workflow, or null on HTTP error
     *   or network failure.
     */
    suspend fun getTicketStatus(ticketKey: String): String?
}

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
class JiraApiService(
    private val httpClient: HttpClient,
    private val cloudId: String,
    private val email: String,
    private val apiToken: String
) : JiraTicketFetcher, JiraCommentPoster, JiraTicketStatusChecker {

    private val log = LoggerFactory.getLogger(JiraApiService::class.java)
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("$email:$apiToken".toByteArray(Charsets.UTF_8))

    private val baseUrl = "https://api.atlassian.com/ex/jira/$cloudId/rest/api/3"

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
    override suspend fun getTicketContent(ticketKey: String): String? {
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
    override suspend fun getTicketStatus(ticketKey: String): String? {
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
    override suspend fun addComment(ticketKey: String, body: String) {
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
