package com.mediasage.pipeline.support

import com.mediasage.agent.db.AgentDatabase
import com.mediasage.agent.db.JobRegistry
import com.mediasage.agent.db.JobRepository
import com.mediasage.agent.db.JobStatus
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.CloudLoggingClient
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.CloudRunJobsClient
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

private const val POLL_INTERVAL_MS = 5_000L

/**
 * Base class for dedup E2E scenarios — requires only [ScenarioConfig.supabaseDbUrl].
 *
 * Sets up a real [JobRegistry] backed by Supabase Postgres. Uses a unique [testKey]
 * per test run so parallel or repeated runs don't collide.
 */
abstract class DedupScenarioBase {

    protected lateinit var config: ScenarioConfig
    protected lateinit var jobRegistry: JobRegistry
    protected lateinit var testKey: String
    protected lateinit var report: ValidationReport

    @BeforeEach
    fun setUpDedup() {
        config = ScenarioConfig.fromEnv()
        AgentDatabase.init(config.supabaseDbUrl)
        jobRegistry = JobRepository()
        testKey = "MS-E2E-${UUID.randomUUID().toString().take(8).uppercase()}"
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
        AgentDatabase.init(config.supabaseDbUrl)
        val jobRepository = JobRepository()
        jobRegistry = jobRepository
        httpClient = buildHttpClient()
        fixture = GitHubFixtureClient(httpClient = httpClient, token = config.githubToken)
        service = AgentLaunchService(
            repoPath = config.repoPath,
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
        val noOpPoster = object : com.mediasage.agent.service.JiraCommentPoster {
            override suspend fun addComment(ticketKey: String, body: String) = Unit
        }
        val client = CloudRunJobsClient(
            httpClient = httpClient,
            projectId = config.gcpProjectId,
            region = config.gcpRegion,
            jobName = config.gcpJobName,
            credentialsJson = config.googleCredentialsJson,
            jobRepository = jobRepository,
            cloudLoggingClient = loggingClient,
            jiraCommentPoster = noOpPoster
        )
        return CloudRunDispatch(client, jobRepository)
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
