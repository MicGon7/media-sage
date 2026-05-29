plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(project(":agent"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    // Ktor client — needed to build CloudRunJobsClient in FullPipelineScenarioBase
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.okhttp)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
}

// Default test task finds 0 tests — all scenarios require credentials and are run via named e2e* tasks.
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("e2e") }
}

// ── E2E tasks ────────────────────────────────────────────────────────────────
// Each task runs one scenario class against real infrastructure.
// Dedup tasks require SUPABASE_DB_URL only.
// Full pipeline tasks require SUPABASE_DB_URL + GCP_PROJECT_ID + GOOGLE_CREDENTIALS_BASE64.

val dedupGroup = "Pipeline E2E — Dedup (requires SUPABASE_DB_URL)"
val pipelineGroup = "Pipeline E2E — Full Pipeline (requires SUPABASE_DB_URL + GCP credentials)"

data class Scenario(val taskName: String, val className: String, val group: String, val description: String)

val scenarios = listOf(
    Scenario(
        taskName = "e2eDedupRunning",
        className = "com.mediasage.pipeline.dedup.DedupRunningE2eTest",
        group = dedupGroup,
        description = "Verifies the dedup gate blocks dispatch when a job is already RUNNING"
    ),
    Scenario(
        taskName = "e2eDedupCompleted",
        className = "com.mediasage.pipeline.dedup.DedupCompletedE2eTest",
        group = dedupGroup,
        description = "Verifies the dedup gate permanently blocks dispatch for a COMPLETED job"
    ),
    Scenario(
        taskName = "e2eDedupFailedRetry",
        className = "com.mediasage.pipeline.dedup.DedupFailedRetryE2eTest",
        group = dedupGroup,
        description = "Verifies a FAILED job is eligible for re-dispatch"
    ),
    Scenario(
        taskName = "e2eConflictResolution",
        className = "com.mediasage.pipeline.pipeline.ConflictResolutionE2eTest",
        group = pipelineGroup,
        description = "Full pipeline: PR dequeued → resolver dispatched → branch rebased → review re-requested"
    ),
    Scenario(
        taskName = "e2ePrReviewResponse",
        className = "com.mediasage.pipeline.pipeline.PrReviewResponseE2eTest",
        group = pipelineGroup,
        description = "Full pipeline: changes_requested review → agent dispatched → fix committed → review re-requested"
    ),
    Scenario(
        taskName = "e2eFailureRecovery",
        className = "com.mediasage.pipeline.pipeline.FailureRecoveryE2eTest",
        group = pipelineGroup,
        description = "Full pipeline: orchestrator restart with RUNNING job → recoverInterruptedJobs() → INTERRUPTED"
    )
)

scenarios.forEach { scenario ->
    tasks.register<Test>(scenario.taskName) {
        group = scenario.group
        description = scenario.description
        useJUnitPlatform { includeTags("e2e") }
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        filter { includeTestsMatching(scenario.className) }
        testLogging { showStandardStreams = true }
        // E2E tasks always hit live infrastructure — never treat as up-to-date
        outputs.upToDateWhen { false }
        // Skip silently when credentials aren't present (CI runs allTests without them)
        onlyIf("SUPABASE_DB_URL must be set to run E2E scenarios") {
            !System.getenv("SUPABASE_DB_URL").isNullOrBlank()
        }
        // Forward all pipeline-relevant env vars to the test JVM
        listOf(
            "SUPABASE_DB_URL",
            "GCP_PROJECT_ID",
            "GCP_REGION",
            "GCP_JOB_NAME",
            "GOOGLE_CREDENTIALS_BASE64",
            "AGENT_REPO_PATH",
            "GH_TOKEN",
            "ORCHESTRATOR_URL",
            "GITHUB_WEBHOOK_SECRET"
        ).forEach { key -> environment(key, System.getenv(key) ?: "") }
    }
}
