package com.mediasage.agent.service

import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.logging.Logger

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
 * Auth: GoogleCredentials loaded from the SA JSON key passed as [credentialsJson].
 * The actual HTTP call reuses the shared Ktor [httpClient].
 *
 * executeJob dispatches the job then polls the returned LRO until completion, so the
 * dedup key in AgentLaunchService is held for the full duration of the worker run.
 *
 * In MS-179 the intelligence seam is the [JobDispatcher] interface itself — a new
 * implementation can enrich the prompt via a Claude API call before delegating here.
 */
class CloudRunJobsClient(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val region: String,
    private val jobName: String,
    private val credentialsJson: String,
    private val agentEnvVars: Map<String, String>
) : JobDispatcher {

    private val log = Logger.getLogger(CloudRunJobsClient::class.java.name)
    private val json = Json { ignoreUnknownKeys = true }

    private val credentials: GoogleCredentials by lazy {
        GoogleCredentials
            .fromStream(ByteArrayInputStream(credentialsJson.toByteArray()))
            .createScoped("https://www.googleapis.com/auth/cloud-platform")
    }

    override suspend fun executeJob(ticketKey: String, prompt: String): Boolean {
        val url = "https://run.googleapis.com/v2/projects/$projectId/locations/$region/jobs/$jobName:run"

        val envVars = agentEnvVars.map { (k, v) -> EnvVar(k, v) } +
            EnvVar("PROMPT", prompt) +
            EnvVar("TICKET_KEY", ticketKey)

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
            log.warning("[$ticketKey] Cloud Run job dispatch failed: ${response.status} — ${response.bodyAsText()}")
            return false
        }

        val operation = json.decodeFromString(OperationResponse.serializer(), response.bodyAsText())
        log.info("[$ticketKey] Cloud Run job dispatched — polling operation ${operation.name}")
        return pollUntilDone(ticketKey, operation.name)
    }

    private suspend fun pollUntilDone(ticketKey: String, operationName: String): Boolean {
        val url = "https://run.googleapis.com/v2/$operationName"
        val deadline = System.currentTimeMillis() + JOB_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val response = httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            }
            if (!response.status.isSuccess()) {
                log.warning("[$ticketKey] Failed to poll operation: ${response.status}")
                continue
            }
            val operation = json.decodeFromString(OperationResponse.serializer(), response.bodyAsText())
            if (operation.done) {
                return if (operation.error != null) {
                    log.warning("[$ticketKey] Cloud Run job failed: ${operation.error.message}")
                    false
                } else {
                    log.info("[$ticketKey] Cloud Run job completed successfully")
                    true
                }
            }
            log.info("[$ticketKey] Cloud Run job still running...")
        }
        log.warning("[$ticketKey] Cloud Run job timed out after 30 minutes")
        return false
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
