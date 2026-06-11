package com.mediasage.pipeline.support

import com.mediasage.agent.db.AgentDatabase
import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRepository
import com.mediasage.pipeline.core.JobStatus
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.CloudLoggingClient
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.CloudRunJobsClient
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val POLL_INTERVAL_MS = 5_000L

/**
 * Base class for dedup E2E scenarios — requires only [PipelineTarget.supabaseDbUrl].
 *
 * Sets up a real [JobRegistry] backed by the target's Supabase Postgres instance.
 * Uses a unique [testKey] per run so parallel or repeated runs don't collide.
 */
abstract class DedupScenarioBase {

    protected lateinit var config: ScenarioConfig
    protected lateinit var jobRegistry: JobRegistry
    protected lateinit var testKey: String
    protected lateinit var report: ValidationReport

    @BeforeEach
    fun setUpDedup() {
        config = ScenarioConfig.fromEnv()
        AgentDatabase.init(config.target.supabaseDbUrl)
        jobRegistry = JobRepository()
        testKey = "${config.target.jiraProjectKey}-E2E-${UUID.randomUUID().toString().take(8).uppercase()}"
        report = ValidationReport(scenarioName())
    }

    abstract fun scenarioName(): String
}

/**
 * Base class for full pipeline E2E scenarios — requires Supabase + GCP credentials.
 *
 * Wires up a real [AgentLaunchService] backed by a real [CloudRunDispatch] and a real
 * [JobRegistry]. Provides [waitForCompletion] for polling Supabase until the Cloud Run
 * Job finishes.
 *
 * The active target (MS, PIPE, etc.) is injected by the Gradle task via env vars —
 * no code changes needed to run against a different client.
 */
abstract class FullPipelineScenarioBase {

    protected lateinit var config: ScenarioConfig
    protected lateinit var jobRegistry: JobRegistry
    protected lateinit var service: AgentLaunchService
    protected lateinit var fixture: GitHubFixtureClient
    protected lateinit var report: ValidationReport
    private lateinit var httpClient: HttpClient
    private val scope = CoroutineScope(Dispatchers.IO)

    @BeforeEach
    fun setUpPipeline() {
        config = ScenarioConfig.fromEnv()
        check(config.gcpProjectId.isNotBlank()) { "GCP_PROJECT_ID is required for full pipeline scenarios" }
        check(config.googleCredentialsJson.isNotBlank()) { "GOOGLE_CREDENTIALS_BASE64 is required for full pipeline scenarios" }
        check(config.target.orchestratorUrl.isNotBlank()) { "ORCHESTRATOR_URL is required for full pipeline scenarios" }
        check(config.target.webhookSecret.isNotBlank()) { "GITHUB_WEBHOOK_SECRET is required for full pipeline scenarios" }
        AgentDatabase.init(config.target.supabaseDbUrl)
        val jobRepository = JobRepository()
        jobRegistry = jobRepository
        httpClient = buildHttpClient()
        fixture = GitHubFixtureClient(
            httpClient = httpClient,
            token = config.githubToken,
            owner = config.target.githubOwner,
            repo = config.target.githubRepo
        )
        service = AgentLaunchService(
            scope = scope,
            cloudRun = buildCloudRunDispatch(jobRepository)
        )
        report = ValidationReport(scenarioName())
    }

    private fun buildCloudRunDispatch(jobRepository: JobRepository): CloudRunDispatch {
        val loggingClient = CloudLoggingClient(
            httpClient = httpClient,
            projectId = config.gcpProjectId,
            credentialsJson = config.googleCredentialsJson
        )
        val client = CloudRunJobsClient(
            httpClient = httpClient,
            projectId = config.gcpProjectId,
            region = config.gcpRegion,
            jobName = config.gcpJobName,
            credentialsJson = config.googleCredentialsJson,
            jobRepository = jobRepository,
            cloudLoggingClient = loggingClient,
        )
        return CloudRunDispatch(client, jobRepository)
    }

    /**
     * POSTs [payload] to the live orchestrator's `/webhook/github` endpoint, simulating a GitHub
     * webhook event. Computes a valid HMAC-SHA256 signature using [PipelineTarget.webhookSecret]
     * so the orchestrator passes signature verification — identical to a real GitHub webhook.
     *
     * Routes to the orchestrator configured for the active [PipelineTarget] — injected
     * by the Gradle task, so switching targets requires no code changes.
     *
     * @param eventType value for the `X-GitHub-Event` header (e.g. `pull_request`, `pull_request_review`)
     * @param payload JSON body — must include `pull_request.user.login = "media-sage-worker[bot]"` to
     *   pass the bot identity gate introduced in MS-258
     * @throws IllegalStateException if the orchestrator responds with a non-2xx status
     */
    protected suspend fun postWebhook(eventType: String, payload: String) {
        val bodyBytes = payload.toByteArray(Charsets.UTF_8)
        val signature = "sha256=${hmacSha256(config.target.webhookSecret, bodyBytes)}"
        val response = httpClient.post("${config.target.orchestratorUrl}/webhook/github") {
            header("X-GitHub-Event", eventType)
            header("X-Hub-Signature-256", signature)
            setBody(TextContent(payload, ContentType.Application.Json))
        }
        check(response.status.isSuccess()) {
            "Webhook POST to orchestrator failed: ${response.status}"
        }
    }

    private fun hmacSha256(secret: String, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    @AfterEach
    fun tearDownPipeline() {
        httpClient.close()
        scope.cancel()
    }

    abstract fun scenarioName(): String

    /**
     * Polls [jobRegistry] every 5 seconds until the job for [ticketKey] reaches
     * a terminal state (COMPLETED, FAILED, or INTERRUPTED), or [timeoutMs] elapses.
     *
     * @return the final [JobStatus], or null if the timeout was reached
     */
    protected suspend fun waitForCompletion(ticketKey: String, timeoutMs: Long): JobStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val terminal = setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.INTERRUPTED)
        while (System.currentTimeMillis() < deadline) {
            val job = jobRegistry.findLatestJob(ticketKey)
            if (job != null && job.status in terminal) return job.status
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun buildHttpClient() = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = false; ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 60_000
        }
    }
}
