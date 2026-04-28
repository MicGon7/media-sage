package com.mediasage.server.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Base64
import java.util.logging.Logger

interface JiraLabelChecker {
    suspend fun isAutonomous(ticketKey: String): Boolean
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

class JiraApiService(
    private val httpClient: HttpClient,
    private val cloudId: String,
    private val email: String,
    private val apiToken: String
) : JiraLabelChecker {

    private val log = Logger.getLogger(JiraApiService::class.java.name)
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("$email:$apiToken".toByteArray(Charsets.UTF_8))

    override suspend fun isAutonomous(ticketKey: String): Boolean {
        return try {
            val response = httpClient.get(
                "https://api.atlassian.com/ex/jira/$cloudId/rest/api/3/issue/$ticketKey?fields=labels"
            ) {
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
}
