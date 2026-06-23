package com.mediasage.orchestrator.service

import com.google.auth.oauth2.GoogleCredentials
import com.mediasage.pipeline.core.JobRepository
import com.mediasage.pipeline.core.WorkerMetrics
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * [onJobCompleted] with metrics already embedded in the event payload (MS-412).
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
            add(EnvVar("JOB_ID", jobId.toString()))
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
     * - Execution done successfully → delegates to [handleDone] (marks COMPLETED), returns true.
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
     * Worker metrics are read directly from [metrics] (embedded in the Pub/Sub payload by
     * the worker, MS-412) and passed to [JobRepository.markCompleted]. No Cloud Logging fetch.
     *
     * @param metrics Parsed from the worker's Pub/Sub event payload. Null for old workers or
     *   the recovery path; the job row is still marked COMPLETED with null metric columns.
     */
    suspend fun onJobCompleted(
        jobId: UUID,
        ticketKey: String,
        succeeded: Boolean,
        failedGate: String? = null,
        metrics: WorkerMetrics? = null,
    ): Boolean {
        return if (succeeded) {
            handleSuccess(jobId, ticketKey, metrics)
        } else {
            log.warn("[$ticketKey] Worker reported failure via Pub/Sub" + (failedGate?.let { " (gate=$it)" } ?: ""))
            jobRepository.markFailed(jobId, failedGate, metrics?.modelVersion)
            false
        }
    }

    private suspend fun handleDone(jobId: UUID, ticketKey: String, operation: OperationResponse): Boolean {
        return if (operation.error != null) {
            log.warn("[$ticketKey] Cloud Run job failed: ${operation.error.message}")
            jobRepository.markFailed(jobId)
            false
        } else {
            // Recovery path: no event metrics available; job is still marked COMPLETED.
            handleSuccess(jobId, ticketKey, metrics = null)
        }
    }

    private suspend fun handleSuccess(
        jobId: UUID,
        ticketKey: String,
        metrics: WorkerMetrics?,
    ): Boolean {
        if (metrics != null) {
            log.info(
                "[$ticketKey] Cloud Run job completed — ${metrics.numTurns} turns, " +
                    "${metrics.inputTokens + metrics.outputTokens} tokens, " +
                    "\$${String.format(java.util.Locale.US, "%.4f", metrics.totalCostUsd)}"
            )
        } else {
            log.warn("[$ticketKey] Cloud Run job completed — no metrics in event (old worker or recovery path)")
        }
        jobRepository.markCompleted(jobId, metrics, envStartupMs = null)
        return true
    }

    private fun accessToken(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}
