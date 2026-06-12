plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(project(":orchestrator"))
    testImplementation(project(":pipelineCore"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.okhttp)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
}

// Default test task finds 0 tests — all scenarios require credentials and are run via named tasks.
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("e2e") }
}

// ── Target configuration ──────────────────────────────────────────────────────
// Holds per-client values. Adding a new client = new TargetConfig entry.
// No changes needed anywhere else.

data class TargetConfig(
    val jiraProjectKey: String,
    val githubOwner: String,
    val githubRepo: String,
    val fixtureTicketKey: String,
    /** Machine env var name to read SUPABASE_DB_URL from (e.g. "SUPABASE_DB_URL", "PIPE_SUPABASE_DB_URL"). */
    val supabaseEnvVar: String,
    val orchestratorEnvVar: String = "ORCHESTRATOR_URL",
    val webhookSecretEnvVar: String = "GITHUB_WEBHOOK_SECRET"
)

val msTarget = TargetConfig(
    jiraProjectKey = "MS",
    githubOwner = "michael-gonzalez-dev",
    githubRepo = "media-sage",
    fixtureTicketKey = "MS-262",
    supabaseEnvVar = "SUPABASE_DB_URL"
)

val pipeTarget = TargetConfig(
    jiraProjectKey = "PIPE",
    githubOwner = "michael-gonzalez-dev",
    githubRepo = "pipeline-sandbox",
    fixtureTicketKey = "PIPE-1",
    supabaseEnvVar = "PIPE_SUPABASE_DB_URL",
    orchestratorEnvVar = "PIPE_ORCHESTRATOR_URL",
    webhookSecretEnvVar = "PIPE_GITHUB_WEBHOOK_SECRET"
)

// ── Scenario registry ─────────────────────────────────────────────────────────

data class Scenario(
    val taskName: String,
    val className: String,
    val group: String,
    val description: String,
    val target: TargetConfig
)

val dedupGroup = "Pipeline E2E — Dedup"
val pipelineGroup = "Pipeline E2E — Full Pipeline"

val scenarios = listOf(
    Scenario(
        "e2eDedupRunning",
        "com.mediasage.pipeline.dedup.DedupRunningE2eTest",
        dedupGroup,
        "Verifies the dedup gate blocks dispatch when a job is already RUNNING",
        msTarget
    ),
    Scenario(
        "e2eDedupCompleted",
        "com.mediasage.pipeline.dedup.DedupCompletedE2eTest",
        dedupGroup,
        "Verifies the dedup gate permanently blocks dispatch for a COMPLETED job",
        msTarget
    ),
    Scenario(
        "e2eDedupFailedRetry",
        "com.mediasage.pipeline.dedup.DedupFailedRetryE2eTest",
        dedupGroup,
        "Verifies a FAILED job is eligible for re-dispatch",
        msTarget
    ),
    Scenario(
        "e2eConflictResolution",
        "com.mediasage.pipeline.pipeline.ConflictResolutionE2eTest",
        pipelineGroup,
        "Full pipeline: PR dequeued → resolver dispatched → branch rebased → review re-requested",
        msTarget
    ),
    Scenario(
        "e2ePrReviewResponse",
        "com.mediasage.pipeline.pipeline.PrReviewResponseE2eTest",
        pipelineGroup,
        "Full pipeline: changes_requested review → agent dispatched → fix committed → review re-requested",
        msTarget
    ),
    Scenario(
        "e2eFailureRecovery",
        "com.mediasage.pipeline.pipeline.FailureRecoveryE2eTest",
        pipelineGroup,
        "Full pipeline: orchestrator restart with RUNNING job → recoverInterruptedJobs() → INTERRUPTED",
        msTarget
    ),
    // PIPE target — same test classes, different target config
    Scenario(
        "pipeE2eDedupRunning",
        "com.mediasage.pipeline.dedup.DedupRunningE2eTest",
        dedupGroup,
        "PIPE target: verifies the dedup gate skips dispatch when a job is already RUNNING",
        pipeTarget
    ),
    Scenario(
        "pipeE2eDedupCompleted",
        "com.mediasage.pipeline.dedup.DedupCompletedE2eTest",
        dedupGroup,
        "PIPE target: verifies the dedup gate permanently blocks dispatch for a COMPLETED job",
        pipeTarget
    ),
    Scenario(
        "pipeE2eDedupFailedRetry",
        "com.mediasage.pipeline.dedup.DedupFailedRetryE2eTest",
        dedupGroup,
        "PIPE target: verifies a FAILED job is retried on re-trigger",
        pipeTarget
    ),
    Scenario(
        "pipeE2ePrReviewResponse",
        "com.mediasage.pipeline.pipeline.PrReviewResponseE2eTest",
        pipelineGroup,
        "PIPE target: full pipeline — changes_requested review → agent dispatched → fix committed",
        pipeTarget
    ),
    Scenario(
        "pipeE2eConflictResolution",
        "com.mediasage.pipeline.pipeline.ConflictResolutionE2eTest",
        pipelineGroup,
        "PIPE target: full pipeline — PR dequeued merge conflict → worker rebases → conflict resolved",
        pipeTarget
    ),
    Scenario(
        "pipeE2eFailureRecovery",
        "com.mediasage.pipeline.pipeline.FailureRecoveryE2eTest",
        pipelineGroup,
        "PIPE target: orchestrator restart with RUNNING job → recoverInterruptedJobs() → INTERRUPTED",
        pipeTarget
    )
)

// ── Task registration ─────────────────────────────────────────────────────────

scenarios.forEach { scenario ->
    tasks.register<Test>(scenario.taskName) {
        group = scenario.group
        description = scenario.description
        useJUnitPlatform { includeTags("e2e") }
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        filter { includeTestsMatching(scenario.className) }
        testLogging { showStandardStreams = true }
        outputs.upToDateWhen { false }

        // Skip silently when target credentials are not present
        onlyIf("${scenario.target.supabaseEnvVar} must be set") {
            !System.getenv(scenario.target.supabaseEnvVar).isNullOrBlank()
        }

        // Per-client config — mapped to standard names the test JVM reads
        environment("JIRA_PROJECT_KEY", scenario.target.jiraProjectKey)
        environment("GITHUB_OWNER", scenario.target.githubOwner)
        environment("GITHUB_REPO", scenario.target.githubRepo)
        environment("FIXTURE_TICKET_KEY", scenario.target.fixtureTicketKey)
        environment("SUPABASE_DB_URL", System.getenv(scenario.target.supabaseEnvVar) ?: "")
        environment("ORCHESTRATOR_URL", System.getenv(scenario.target.orchestratorEnvVar) ?: "")
        environment(
            "GITHUB_WEBHOOK_SECRET",
            System.getenv(scenario.target.webhookSecretEnvVar) ?: ""
        )

        // Shared infrastructure — forwarded unchanged
        listOf(
            "GCP_PROJECT_ID",
            "GCP_REGION",
            "GCP_JOB_NAME",
            "GOOGLE_CREDENTIALS_BASE64",
            "AGENT_REPO_PATH",
            "GH_TOKEN"
        )
            .forEach { key -> environment(key, System.getenv(key) ?: "") }
    }
}
