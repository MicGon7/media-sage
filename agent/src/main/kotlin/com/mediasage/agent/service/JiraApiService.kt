package com.mediasage.agent.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.logging.Logger

interface JiraLabelChecker {
    suspend fun isAutonomous(ticketKey: String): Boolean
}

interface JiraTicketFetcher {
    suspend fun getTicketContent(ticketKey: String): String?
}

@Serializable
private data class JiraIssueLabelsResponse(
    @SerialName("fields")
    val fields: JiraLabelsFields
)

@Serializable
private data class JiraLabelsFields(
    @SerialName("labels")
    val labels: List<String> = emptyList()
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

class JiraApiService(
    private val httpClient: HttpClient,
    private val cloudId: String,
    private val email: String,
    private val apiToken: String
) : JiraLabelChecker, JiraTicketFetcher {

    private val log = Logger.getLogger(JiraApiService::class.java.name)
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("$email:$apiToken".toByteArray(Charsets.UTF_8))

    private val baseUrl = "https://api.atlassian.com/ex/jira/$cloudId/rest/api/3"

    override suspend fun isAutonomous(ticketKey: String): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/issue/$ticketKey?fields=labels") {
                header(HttpHeaders.Authorization, authHeader)
                accept(ContentType.Application.Json)
            }
            val body = response.body<JiraIssueLabelsResponse>()
            "autonomous" in body.fields.labels
        } catch (e: Exception) {
            log.warning("Failed to check Jira labels for $ticketKey: ${e.message}")
            false
        }
    }

    override suspend fun getTicketContent(ticketKey: String): String? {
        return try {
            val response = httpClient.get("$baseUrl/issue/$ticketKey?fields=summary,description") {
                header(HttpHeaders.Authorization, authHeader)
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                log.warning("Jira API returned ${response.status} for $ticketKey content fetch")
                return null
            }
            val body = response.body<JiraIssueContentResponse>()
            val fields = body.fields ?: run {
                log.warning("Jira response for $ticketKey had no fields")
                return null
            }
            val description = fields.description?.let { extractText(it) }.orEmpty()
            "**$ticketKey: ${fields.summary}**\n\n$description"
        } catch (e: Exception) {
            log.warning("Failed to fetch ticket content for $ticketKey: ${e.message}")
            null
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
