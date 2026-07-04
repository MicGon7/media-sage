package com.mediasage.agentruntime.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

// ---- DTOs for issue-link queries ----

@Serializable
private data class JiraIssueLinkResponse(
    @SerialName("fields")
    val fields: IssueLinkFields? = null
)

@Serializable
private data class IssueLinkFields(
    @SerialName("issuelinks")
    val issueLinks: List<JiraIssueLink> = emptyList(),
    @SerialName("assignee")
    val assignee: LinkAssignee? = null,
    @SerialName("status")
    val status: LinkStatus? = null,
)

@Serializable
private data class JiraIssueLink(
    @SerialName("type")
    val type: LinkType,
    @SerialName("outwardIssue")
    val outwardIssue: LinkedIssue? = null,
    @SerialName("inwardIssue")
    val inwardIssue: LinkedIssue? = null,
)

@Serializable
private data class LinkType(
    @SerialName("name")
    val name: String,
)

@Serializable
private data class LinkedIssue(
    @SerialName("key")
    val key: String,
    @SerialName("fields")
    val fields: LinkedIssueFields? = null,
)

@Serializable
private data class LinkedIssueFields(
    @SerialName("status")
    val status: LinkStatus? = null,
)

@Serializable
private data class LinkStatus(
    @SerialName("name")
    val name: String = "",
)

@Serializable
private data class LinkAssignee(
    @SerialName("accountId")
    val accountId: String = "",
)

private const val LINK_TYPE_BLOCKS = "Blocks"
private const val STATUS_DONE = "Done"

/**
 * Jira implementation of [TicketSystemClient].
 *
 * Contains all Jira-specific logic: link-type filtering ("Blocks"), status name matching
 * ("Done"), and REST call sequencing. No Jira types or field names appear in dispatch logic —
 * callers reference only [TicketSystemClient].
 *
 * @param httpClient Ktor HTTP client.
 * @param cloudId Atlassian cloud instance ID.
 * @param email Atlassian account email for Basic auth.
 * @param apiToken Atlassian API token.
 * @param botAccountId Jira account ID of the bot. Only tickets assigned to this account
 *   are returned by [getNewlyUnblockedTickets].
 */
open class JiraTicketClient(
    httpClient: HttpClient,
    cloudId: String,
    email: String,
    apiToken: String,
    private val botAccountId: String,
) : JiraApiClient(httpClient, cloudId, email, apiToken), TicketSystemClient {

    private val log = LoggerFactory.getLogger(JiraTicketClient::class.java)

    override suspend fun isResolved(ticketKey: String): Boolean =
        getTicketStatus(ticketKey) == STATUS_DONE

    override suspend fun getNewlyUnblockedTickets(mergedTicketKey: String): List<String> {
        val mergedIssue = fetchIssueLinks(mergedTicketKey) ?: return emptyList()

        val outwardBlocked = mergedIssue.fields?.issueLinks.orEmpty()
            .filter { it.type.name == LINK_TYPE_BLOCKS && it.outwardIssue != null }
            .mapNotNull { it.outwardIssue?.key }

        if (outwardBlocked.isEmpty()) return emptyList()

        return outwardBlocked.mapNotNull { candidateKey ->
            isFullyUnblockedAndBotAssigned(candidateKey, mergedTicketKey)
        }
    }

    // mergedTicketKey is treated as resolved regardless of its current Jira status —
    // the GitHub webhook fires before GitHub Actions auto-transitions the blocker to Done.
    private suspend fun isFullyUnblockedAndBotAssigned(candidateKey: String, mergedTicketKey: String): String? {
        val candidate = fetchIssueLinks(candidateKey) ?: return null
        val fields = candidate.fields ?: return null
        if (fields.assignee?.accountId != botAccountId) return null

        val allBlockersDone = fields.issueLinks
            .filter { it.type.name == LINK_TYPE_BLOCKS && it.inwardIssue != null }
            .all { link ->
                link.inwardIssue!!.key == mergedTicketKey ||
                    link.inwardIssue.fields?.status?.name == STATUS_DONE
            }

        return if (allBlockersDone) candidateKey else null
    }

    private suspend fun fetchIssueLinks(ticketKey: String): JiraIssueLinkResponse? {
        return try {
            val response = httpClient.get(
                "$baseUrl/issue/$ticketKey?fields=issuelinks,assignee,status"
            ) {
                header(HttpHeaders.Authorization, authHeader)
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                log.warn("Jira API returned ${response.status} for $ticketKey issue-links fetch")
                return null
            }
            response.body<JiraIssueLinkResponse>()
        } catch (e: Exception) {
            log.warn("Failed to fetch issue links for $ticketKey: ${e.message}")
            null
        }
    }
}
