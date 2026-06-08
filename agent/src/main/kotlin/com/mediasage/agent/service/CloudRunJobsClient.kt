package com.mediasage.agent.service

import com.google.auth.oauth2.GoogleCredentials
import com.mediasage.agent.db.JobRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
 * Calls the Cloud Run Jobs Admin API to dispatch the worker job with per-run env var overrides.
 *
 * Dispatches the job and marks it RUNNING, then returns immediately. Completion is signalled
 * by the worker itself via a Pub/Sub push event to [POST /webhook/pubsub], which calls
 * [onJobCompleted] to fetch metrics and update the job row.
 *
 * [recoverJob] is the startup safety net: on restart it checks RUNNING jobs whose Pub/Sub
 * event may have been missed while the orchestrator was down.
 */
class CloudRunJobsClient(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val region: String,
    private val jobName: String,
    private val credentialsJson: String,
    internal val jobRepository: JobRepository,
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

    /**
     * Dispatches a Cloud Run Job execution via the Admin API and marks the job RUNNING.
     *
     * Posts to `v2/projects/{project}/locations/{region}/jobs/{jobName}:run` with per-run env var
     * overrides: `PROMPT`, `TICKET_KEY`, and optionally `JIRA_TICKET_KEY` (when [jiraTicketKey]
     * differs from [ticketKey], e.g. for PR reviews where [ticketKey] is `PR-{prNumber}`).
     *
     * The API response includes an operation name used as the execution name — saved via
     * [JobRepository.markRunning] so [recoverJob] can look it up after a restart. Returns
     * immediately; job completion is signalled asynchronously via Pub/Sub → [onJobCompleted].
     *
     * @return true if the API call succeeded and the job was marked RUNNING; false on HTTP error.
     */
    override suspend fun executeJob(
        jobId: UUID,
        ticketKey: String,
        prompt: String,
        jiraTicketKey: String?,
        jobNameOverride: String?,
    ): Boolean {
        val resolvedJobName = jobNameOverride ?: jobName
        val url = "https://run.googleapis.com/v2/projects/$projectId/locations/$region/jobs/$resolvedJobName:run"

        val envVars = buildList {
            add(EnvVar("PROMPT", prompt))
            add(EnvVar("TICKET_KEY", ticketKey))
            if (jiraTicketKey != null) add(EnvVar("JIRA_TICKET_KEY", jiraTicketKey))
        }

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
            jobRepository.markFailed(jobId)
            return false
        }

        val operation = json.decodeFromString(OperationResponse.serializer(), response.bodyAsText())
        log.info("[$ticketKey] Cloud Run job dispatched — awaiting Pub/Sub completion event (operation: ${operation.name})")
        jobRepository.markRunning(jobId, operation.name)
        return true
    }

    /**
     * Checks whether the Cloud Run execution identified by [executionName] is still alive,
     * and reconciles the job row accordingly.
     *
     * Called at orchestrator startup for every job left in RUNNING state (see
     * [AgentLaunchService.recoverInterruptedJobs]). The [executionName] was saved by
     * [executeJob] via [JobRepository.markRunning], so no additional executions-list call is needed.
     *
     * Outcomes:
     * - Execution not found (404) → job marked INTERRUPTED, returns false.
     * - Execution done with error → job marked FAILED, returns false.
     * - Execution done successfully → delegates to [handleDone] (marks COMPLETED, fetches metrics), returns true.
     * - Execution still running → no-op; Pub/Sub will fire [onJobCompleted] on completion, returns false.
     *
     * @param executionName Full Cloud Run execution resource name, e.g.
     *   `projects/my-project/locations/us-central1/jobs/my-job/executions/my-job-dtz62`.
     * @return true only if the execution was already done and completion was handled successfully.
     */
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
            log.info("[$ticketKey] Recovery: execution already done — processing completion")
            handleDone(jobId, ticketKey, operation)
        } else {
            log.info("[$ticketKey] Recovery: execution still running — Pub/Sub event expected on completion")
            false
        }
    }

    /**
     * Called by the Pub/Sub webhook route when the worker signals completion.
     *
     * @param executionName Short Cloud Run execution name (e.g. `media-sage-agent-worker-dtz62`),
     *   passed directly from the worker's [CLOUD_RUN_EXECUTION] env var. Used to look up metrics
     *   in Cloud Logging without an additional executions list API call.
     */
    suspend fun onJobCompleted(
        jobId: UUID,
        ticketKey: String,
        executionName: String,
        succeeded: Boolean,
        commentBody: String? = null,
        jiraTicketKey: String? = null,
        wallClockMs: Long? = null
    ): Boolean {
        return if (succeeded) {
            handleSuccess(jobId, ticketKey, executionName, commentBody, jiraTicketKey, wallClockMs)
        } else {
            log.warn("[$ticketKey] Worker reported failure via Pub/Sub")
            jobRepository.markFailed(jobId)
            false
        }
    }

    private suspend fun handleDone(jobId: UUID, ticketKey: String, operation: OperationResponse): Boolean {
        return if (operation.error != null) {
            log.warn("[$ticketKey] Cloud Run job failed: ${operation.error.message}")
            jobRepository.markFailed(jobId)
            false
        } else {
            val executionName = fetchLatestExecutionName(ticketKey)
            log.info("[$ticketKey] Latest execution name: $executionName")
            handleSuccess(jobId, ticketKey, executionName)
        }
    }

    private suspend fun handleSuccess(
        jobId: UUID,
        ticketKey: String,
        executionName: String?,
        commentBody: String? = null,
        jiraTicketKey: String? = null,
        wallClockMs: Long? = null
    ): Boolean {
        log.info("[$ticketKey] Cloud Run job completed successfully — fetching worker metrics")
        val effectiveJiraKey = jiraTicketKey ?: ticketKey
        val metrics = if (executionName != null) {
            cloudLoggingClient.fetchMetrics(executionName).also { m ->
                if (m != null) {
                    log.info(
                        "[$ticketKey] Metrics: ${m.numTurns} turns, " +
                            "${m.inputTokens + m.outputTokens} tokens, " +
                            "\$${String.format(java.util.Locale.US, "%.4f", m.totalCostUsd)}"
                    )
                    postConsolidatedComment(effectiveJiraKey, m, commentBody, wallClockMs)
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
     * Lists executions for the job and returns the name of the most recent one.
     * Used only in the recovery path when the execution name is not available from a Pub/Sub event.
     */
    private suspend fun fetchLatestExecutionName(ticketKey: String): String? {
        val url = "https://run.googleapis.com/v2/projects/$projectId/locations/$region/jobs/$jobName/executions" +
            "?pageSize=1"
        return runCatching {
            val httpResponse = httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            }
            if (!httpResponse.status.isSuccess()) {
                log.warn("[$ticketKey] Failed to list executions: ${httpResponse.status}")
                return@runCatching null
            }
            json.parseToJsonElement(httpResponse.bodyAsText())
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

    /**
     * Posts the consolidated run metrics comment to the Jira ticket as Media Sage Bot.
     *
     * Combines the rich comment body written by Claude (pipeline checkpoints, PR link, quality
     * gates, AC) with accurate metrics fetched from Cloud Logging (turns, tokens, cost, duration).
     * If no comment body was provided (e.g. worker exited before writing the file), falls back
     * to a plain metrics-only comment.
     *
     * Example comment:
     * ```
     * 🤖 Agent: Run metrics summary for MS-XXX
     *
     * Task: ...
     * Pipeline checkpoints verified:
     * ✅ ...
     * PR: https://github.com/...
     * ...
     *
     * Run metrics:
     * • Turns: 47 · Duration: 1h 23m 5s
     * • Tokens: 11,809 input · 38,211 output · 4,653,492 cached
     * • Cost: $2.0007
     * ```
     */
    private suspend fun postConsolidatedComment(
        ticketKey: String,
        m: com.mediasage.agent.db.WorkerMetrics,
        commentBody: String?,
        wallClockMs: Long? = null
    ) {
        // Prefer wall-clock duration (job dispatch → Pub/Sub receipt) over Claude API time.
        // m.durationMs from Cloud Logging only measures time inside Claude API calls — it
        // excludes container cold start, GitHub token generation, and git clone (~1-3 min overhead).
        val durationStr = formatDuration(wallClockMs ?: m.durationMs)
        val metricsSection = buildString {
            appendLine("Run metrics:")
            appendLine(
                "• Turns: ${m.numTurns} · Duration: $durationStr"
            )
            appendLine(
                "• Tokens: ${"%,d".format(m.inputTokens)} input · " +
                    "${"%,d".format(m.outputTokens)} output · " +
                    "${"%,d".format(m.cacheReadTokens)} cached"
            )
            append("• Cost: \$${String.format(java.util.Locale.US, "%.4f", m.totalCostUsd)}")
        }
        // Resolve any pending checkpoints the agent wrote before exiting — if the orchestrator
        // is posting this comment, Pub/Sub has already fired and Supabase is already updated.
        val resolvedBody = commentBody?.replace("⏳", "✅")
        val comment = if (!resolvedBody.isNullOrBlank()) {
            "${resolvedBody.trimEnd()}\n\n$metricsSection"
        } else {
            // Fallback: worker exited before writing the comment file
            "🤖 Agent: Run metrics summary for $ticketKey\n\n$metricsSection"
        }
        runCatching { jiraCommentPoster.addComment(ticketKey, comment) }
            .onFailure { log.warn("[$ticketKey] Failed to post consolidated comment to Jira: ${it.message}") }
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
}
