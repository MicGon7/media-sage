package com.mediasage.agent.service

import com.google.auth.oauth2.GoogleCredentials
import com.mediasage.agent.db.JobRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.UUID
import org.slf4j.LoggerFactory

@Serializable
private data class EnvVar(
    @SerialName("name") val name: String,
    @SerialName("value") val value: String
)

@Serializable
private data class ContainerOverride(
    @SerialName("env") val env: List<EnvVar>
)

@Serializable
private data class Overrides(
    @SerialName("containerOverrides") val containerOverrides: List<ContainerOverride>,
    @SerialName("taskCount") val taskCount: Int = 1,
    @SerialName("timeout") val timeout: String = "1800s"
)

@Serializable
private data class RunJobRequest(
    @SerialName("overrides") val overrides: Overrides
)

@Serializable
private data class OperationResponse(
    @SerialName("name") val name: String,
    @SerialName("done") val done: Boolean = false,
    @SerialName("error") val error: OperationError? = null
)

@Serializable
private data class OperationError(
    @SerialName("code") val code: Int = 0,
    @SerialName("message") val message: String = ""
)

/**
 * Calls the Cloud Run Jobs Admin API to execute the worker job with per-run env var overrides.
 *
 * Dispatches the job, marks it RUNNING with the execution name, then polls the LRO until
 * completion before updating the job row to COMPLETED or FAILED. This keeps the job row
 * accurate for the full duration of the worker run.
 *
 * The [JobDispatcher] interface is the intelligence seam for MS-179 — a future implementation
 * can enrich the prompt before delegating here.
 */
class CloudRunJobsClient(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val region: String,
    private val jobName: String,
    private val credentialsJson: String,
    private val jobRepository: JobRepository,
    private val cloudLoggingClient: CloudLoggingClient,
    private val jiraCommentPoster: JiraCommentPoster
) : JobDispatcher {

    private val log = LoggerFactory.getLogger(CloudRunJobsClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val credentials: GoogleCredentials by lazy {
        GoogleCredentials
            .fromStream(ByteArrayInputStream(credentialsJson.toByteArray()))
            .createScoped("https://www.googleapis.com/auth/cloud-platform")
    }

    override suspend fun executeJob(jobId: UUID, ticketKey: String, prompt: String): Boolean {
        val url = "https://run.googleapis.com/v2/projects/$projectId/locations/$region/jobs/$jobName:run"

        val envVars = listOf(
            EnvVar("PROMPT", prompt),
            EnvVar("TICKET_KEY", ticketKey)
        )

        val body = json.encodeToString(
            RunJobRequest.serializer(),
            RunJobRequest(Overrides(listOf(ContainerOverride(envVars))))
        )

        val response = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (!response.status.isSuccess()) {
            log.warn("[$ticketKey] Cloud Run job dispatch failed: ${response.status} — ${response.bodyAsText()}")
            return false
        }

        val operation = json.decodeFromString(OperationResponse.serializer(), response.bodyAsText())
        log.info("[$ticketKey] Cloud Run job dispatched — polling operation ${operation.name}")
        jobRepository.markRunning(jobId, operation.name)
        return pollUntilDone(jobId, ticketKey, operation.name)
    }

    override suspend fun recoverJob(jobId: UUID, ticketKey: String, executionName: String): Boolean {
        val url = "https://run.googleapis.com/v2/$executionName"
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
        }
        if (!response.status.isSuccess()) {
            log.warn("[$ticketKey] Recovery: execution not found (${response.status}) — marking INTERRUPTED")
            jobRepository.markInterrupted(jobId)
            return false
        }
        val operation = json.decodeFromString(OperationResponse.serializer(), response.bodyAsText())
        return if (operation.done) {
            handleDone(jobId, ticketKey, operation)
        } else {
            log.info("[$ticketKey] Recovery: execution still running — resuming poll")
            pollUntilDone(jobId, ticketKey, executionName)
        }
    }

    private suspend fun pollUntilDone(jobId: UUID, ticketKey: String, operationName: String): Boolean {
        val url = "https://run.googleapis.com/v2/$operationName"
        val deadline = System.currentTimeMillis() + JOB_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val response = httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            }
            if (!response.status.isSuccess()) {
                log.warn("[$ticketKey] Failed to poll operation: ${response.status}")
                continue
            }
            val operation = json.decodeFromString(OperationResponse.serializer(), response.bodyAsText())
            if (operation.done) return handleDone(jobId, ticketKey, operation)
            log.info("[$ticketKey] Cloud Run job still running...")
        }
        log.warn("[$ticketKey] Cloud Run job timed out after 30 minutes")
        jobRepository.markFailed(jobId)
        return false
    }

    /**
     * Lists executions for the job sorted by createTime descending and returns the name
     * of the most recent one. This is more reliable than reading [OperationResponse.response]
     * because the GCP Cloud Run v2 REST API does not always populate the `response` field
     * in the completed LRO JSON.
     */
    private suspend fun fetchLatestExecutionName(ticketKey: String): String? {
        val url = "https://run.googleapis.com/v2/projects/$projectId/locations/$region/jobs/$jobName/executions" +
            "?pageSize=1"
        return runCatching {
            val httpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            }
            val body = httpResponse.bodyAsText()
            log.info("[$ticketKey] Executions API status: ${httpResponse.status}, body: ${body.take(300)}")
            if (!httpResponse.status.isSuccess()) {
                log.warn("[$ticketKey] Failed to list executions: ${httpResponse.status}")
                return@runCatching null
            }
            json.parseToJsonElement(body)
                .jsonObject["executions"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("name")
                ?.jsonPrimitive
                ?.content
        }.getOrElse {
            log.warn("[$ticketKey] Error fetching latest execution name: ${it.message}", it)
            null
        }
    }

    private suspend fun handleDone(jobId: UUID, ticketKey: String, operation: OperationResponse): Boolean {
        return if (operation.error != null) {
            log.warn("[$ticketKey] Cloud Run job failed: ${operation.error.message}")
            jobRepository.markFailed(jobId)
            false
        } else {
            handleSuccess(jobId, ticketKey)
        }
    }

    private suspend fun handleSuccess(jobId: UUID, ticketKey: String): Boolean {
        log.info("[$ticketKey] Cloud Run job completed successfully — fetching worker metrics")
        val executionName = fetchLatestExecutionName(ticketKey)
        log.info("[$ticketKey] Latest execution name: $executionName")
        val metrics = if (executionName != null) {
            cloudLoggingClient.fetchMetrics(executionName).also { m ->
                if (m != null) {
                    log.info(
                        "[$ticketKey] Metrics: ${m.numTurns} turns, " +
                            "${m.inputTokens + m.outputTokens} tokens, " +
                            "\$${String.format(java.util.Locale.US, "%.4f", m.totalCostUsd)}"
                    )
                    postMetricsComment(ticketKey, m)
                } else {
                    log.warn("[$ticketKey] Worker metrics unavailable — job marked complete without cost data")
                }
            }
        } else {
            log.warn("[$ticketKey] Could not determine execution name — skipping metrics fetch")
            null
        }
        jobRepository.markCompleted(jobId, metrics)
        return true
    }

    /**
     * Posts a human-readable worker run summary to the Jira ticket.
     *
     * Example comment:
     * ```
     * 🤖 Worker run complete
     * • Turns: 47
     * • Duration: 1h 23m 5s
     * • Tokens: 11,809 input · 38,211 output · 4,653,492 cached
     * • Cost: $2.0007
     * ```
     */
    private suspend fun postMetricsComment(ticketKey: String, m: com.mediasage.agent.db.WorkerMetrics) {
        val durationStr = formatDuration(m.durationMs)
        val comment = buildString {
            appendLine("🤖 **Worker run complete**")
            appendLine("• **Turns:** ${m.numTurns}")
            appendLine("• **Duration:** $durationStr")
            appendLine(
                "• **Tokens:** ${"%,d".format(m.inputTokens)} input · " +
                    "${"%,d".format(m.outputTokens)} output · " +
                    "${"%,d".format(m.cacheReadTokens)} cached"
            )
            append("• **Cost:** \$${String.format(java.util.Locale.US, "%.4f", m.totalCostUsd)}")
        }
        runCatching { jiraCommentPoster.addComment(ticketKey, comment) }
            .onFailure { log.warn("[$ticketKey] Failed to post metrics comment to Jira: ${it.message}") }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun accessToken(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val JOB_TIMEOUT_MS = 1_800_000L
    }
}
